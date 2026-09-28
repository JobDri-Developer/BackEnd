package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.internal.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisPreparationDataLoader.LoadedPreparationData;
import com.jobdri.jobdri_api.domain.analysis.service.ai.JobCategoryEvaluationCriteriaProvider;
import com.jobdri.jobdri_api.domain.analysis.service.retrieval.JobPostingRagContextAssembler;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisPreparationService {

    private final AnalysisPreparationDataLoader dataLoader;
    private final JobCategoryEvaluationCriteriaProvider jobCategoryEvaluationCriteriaProvider;
    private final CorpusRetrievalService corpusRetrievalService;
    private final JobPostingRagContextAssembler jobPostingRagContextAssembler;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AnalysisPreparationResult prepare(User user, Long mockApplyId) {
        PreparedMockApplyContext context = prepareMockApplyContext(user, mockApplyId);
        return prepare(
                user.getId(),
                context,
                retrieveAnalysisReferences(context.mockApply().getJobPosting(), context.answeredQuestions()),
                jobPostingRagContextAssembler.assemble(context.mockApply().getJobPosting().getId())
        );
    }

    public AnalysisPreparationResult prepare(
            User user,
            Long mockApplyId,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        PreparedMockApplyContext context = prepareMockApplyContext(user, mockApplyId);
        return prepare(
                user.getId(),
                context,
                new RetrievalContext(List.of(), List.of()),
                similarJobPostings
        );
    }

    private AnalysisPreparationResult prepare(
            Long userId,
            PreparedMockApplyContext context,
            RetrievalContext retrievalContext,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        JobCategoryEvaluationCriteria evaluationCriteria = jobCategoryEvaluationCriteriaProvider
                .findByMiddleName(context.mockApply().getJobPosting().getDetailClassification().getMiddleClassification().getMiddleName())
                .orElse(null);

        return new AnalysisPreparationResult(
                userId,
                context.mockApply().getId(),
                context.mockApply().getJobPosting(),
                context.questions(),
                context.answeredQuestions(),
                evaluationCriteria,
                retrievalContext,
                similarJobPostings
        );
    }

    private PreparedMockApplyContext prepareMockApplyContext(User user, Long mockApplyId) {
        LoadedPreparationData data = dataLoader.load(user, mockApplyId);
        MockApply mockApply = data.mockApply();
        List<Question> questions = data.questions();
        return new PreparedMockApplyContext(
                mockApply,
                questions,
                answeredQuestionsOrThrow(questions)
        );
    }

    private List<Question> answeredQuestionsOrThrow(List<Question> questions) {
        List<Question> answeredQuestions = questions.stream()
                .filter(question -> StringUtils.hasText(question.getAnswer()))
                .toList();
        if (answeredQuestions.isEmpty()) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "분석할 자소서 답변이 1개 이상 필요합니다."
            );
        }
        return answeredQuestions;
    }

    private RetrievalContext retrieveAnalysisReferences(JobPosting jobPosting, List<Question> answeredQuestions) {
        try {
            return corpusRetrievalService.retrieveForAnalysis(jobPosting, answeredQuestions);
        } catch (Exception exception) {
            log.warn("자소서 분석 Curated Corpus retrieval 실패. fallback without references. message={}", exception.getMessage());
            log.debug("analysis Curated Corpus retrieval exception", exception);
            return new RetrievalContext(List.of(), List.of());
        }
    }

    private record PreparedMockApplyContext(
            MockApply mockApply,
            List<Question> questions,
            List<Question> answeredQuestions
    ) {
    }
}
