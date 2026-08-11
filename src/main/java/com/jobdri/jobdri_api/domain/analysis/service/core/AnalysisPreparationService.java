package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.analysis.service.ai.JobCategoryEvaluationCriteriaProvider;
import com.jobdri.jobdri_api.domain.analysis.service.retrieval.JobPostingRagContextAssembler;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisPreparationService {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final JobCategoryEvaluationCriteriaProvider jobCategoryEvaluationCriteriaProvider;
    private final CorpusRetrievalService corpusRetrievalService;
    private final JobPostingRagContextAssembler jobPostingRagContextAssembler;

    public AnalysisPreparationResult prepare(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<Question> answeredQuestions = answeredQuestionsOrThrow(questions);
        return prepare(
                user,
                mockApply,
                questions,
                answeredQuestions,
                retrieveAnalysisReferences(mockApply.getJobPosting(), answeredQuestions),
                jobPostingRagContextAssembler.assemble(mockApply.getJobPosting().getId())
        );
    }

    public AnalysisPreparationResult prepare(
            User user,
            Long mockApplyId,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<Question> answeredQuestions = answeredQuestionsOrThrow(questions);
        return prepare(
                user,
                mockApply,
                questions,
                answeredQuestions,
                new RetrievalContext(List.of(), List.of()),
                similarJobPostings
        );
    }

    private AnalysisPreparationResult prepare(
            User user,
            MockApply mockApply,
            List<Question> questions,
            List<Question> answeredQuestions,
            RetrievalContext retrievalContext,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        // Initialize hierarchy before leaving the read transaction so detached payload can be used safely.
        mockApply.getJobPosting().getDetailClassification().getMiddleClassification().getMiddleName();
        mockApply.getJobPosting().getDetailClassification().getMiddleClassification().getClassification().getBigName();
        JobCategoryEvaluationCriteria evaluationCriteria = jobCategoryEvaluationCriteriaProvider
                .findByMiddleName(mockApply.getJobPosting().getDetailClassification().getMiddleClassification().getMiddleName())
                .orElse(null);

        return new AnalysisPreparationResult(
                user.getId(),
                mockApply.getId(),
                mockApply.getJobPosting(),
                questions,
                answeredQuestions,
                evaluationCriteria,
                retrievalContext,
                similarJobPostings
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

    private MockApply getOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findById(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
    }
}
