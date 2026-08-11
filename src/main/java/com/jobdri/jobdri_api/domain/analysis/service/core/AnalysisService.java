package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.analysis.service.ai.JobCategoryEvaluationCriteriaProvider;
import com.jobdri.jobdri_api.domain.analysis.service.retrieval.JobPostingRagContextAssembler;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.payment.service.CreditService;
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
// 자소서 분석 실행 흐름을 조율하는 오케스트레이션 서비스다.
public class AnalysisService {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final JobPostingService jobPostingService;
    private final AnalysisAiClient analysisAiClient;
    private final CreditService creditService;
    private final JobCategoryEvaluationCriteriaProvider jobCategoryEvaluationCriteriaProvider;
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;
    private final CorpusRetrievalService corpusRetrievalService;
    private final JobPostingRagContextAssembler jobPostingRagContextAssembler;
    private final AnalysisResultPersistenceService analysisResultPersistenceService;

    @Transactional
    @AuditLogEvent(action = "ANALYSIS_RUN", targetType = "MOCK_APPLY", targetId = "#arg1")
    public AnalysisResponse analyze(User user, Long mockApplyId) {
        validateAnalysisRequest(user, mockApplyId);
        AnalysisExecutionPayload payload = prepareAnalysisExecution(user, mockApplyId);
        String inputFingerprint = analysisInputFingerprintProvider.create(payload);
        lockOwnedMockApply(user, mockApplyId);
        AnalysisResponse cachedResponse = reuseExistingAnalysisIfSameInput(user, mockApplyId, inputFingerprint);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        String referenceId = analysisCreditReferenceId(mockApplyId, inputFingerprint);
        deductAnalysisCredit(user, referenceId);

        try {
            AnalysisLlmResponse llmResponse = executeAnalysis(payload);
            return finalizeAnalysis(user, mockApplyId, payload, llmResponse);
        } catch (RuntimeException e) {
            refundAnalysisCredit(user, referenceId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public void validateAnalysisRequest(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        answeredQuestionsOrThrow(questions);
    }

    @Transactional
    public void deductAnalysisCredit(User user, String referenceId) {
        creditService.use(user, 1, "자소서 분석 크레딧 차감", referenceId);
    }

    @Transactional
    public void refundAnalysisCredit(User user, String referenceId) {
        creditService.refund(user, 1, "자소서 분석 크레딧 환불", referenceId);
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAnalysisExecution(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<Question> answeredQuestions = answeredQuestionsOrThrow(questions);
        return prepareAnalysisExecution(
                user,
                mockApply,
                questions,
                answeredQuestions,
                retrieveAnalysisReferences(mockApply.getJobPosting(), answeredQuestions),
                jobPostingRagContextAssembler.assemble(mockApply.getJobPosting().getId())
        );
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAnalysisExecution(
            User user,
            Long mockApplyId,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<Question> answeredQuestions = answeredQuestionsOrThrow(questions);
        return prepareAnalysisExecution(
                user,
                mockApply,
                questions,
                answeredQuestions,
                new RetrievalContext(List.of(), List.of()),
                similarJobPostings
        );
    }

    private AnalysisExecutionPayload prepareAnalysisExecution(
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

        return new AnalysisExecutionPayload(
                user.getId(),
                mockApply.getId(),
                mockApply.getJobPosting(),
                List.copyOf(questions),
                List.copyOf(answeredQuestions),
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

    public AnalysisLlmResponse executeAnalysis(AnalysisExecutionPayload payload) {
        return analysisAiClient.analyze(payload);
    }

    @Transactional
    public AnalysisResponse finalizeAnalysis(
            User user,
            Long mockApplyId,
            AnalysisExecutionPayload payload,
            AnalysisLlmResponse llmResponse
    ) {
        return finalizeAnalysis(
                user,
                mockApplyId,
                payload,
                llmResponse,
                analysisInputFingerprintProvider.create(payload)
        );
    }

    @Transactional
    public AnalysisResponse finalizeAnalysis(
            User user,
            Long mockApplyId,
            AnalysisExecutionPayload payload,
            AnalysisLlmResponse llmResponse,
            String inputFingerprint
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        return analysisResultPersistenceService.finalizeAnalysis(
                mockApply,
                questions,
                payload.answerSnapshots(),
                llmResponse,
                inputFingerprint
        );
    }

    @Transactional
    public AnalysisResponse getAnalysis(User user, Long mockApplyId) {
        return analysisResultPersistenceService.getPersistedAnalysis(getOwnedMockApply(user, mockApplyId));
    }

    @Transactional
    public AnalysisResponse getAnalysisByJobPostingSequence(User user, Long jobPostingId, int sequence) {
        JobPosting jobPosting = jobPostingService.getOwnedJobPosting(user, jobPostingId);
        MockApply mockApply = resolveMockApplyBySequence(jobPosting, sequence);
        return analysisResultPersistenceService.getPersistedAnalysis(mockApply);
    }

    @Transactional(readOnly = true)
    public boolean hasReusableAnalysis(User user, Long mockApplyId) {
        AnalysisExecutionPayload payload = prepareAnalysisExecution(user, mockApplyId);
        String inputFingerprint = analysisInputFingerprintProvider.create(payload);
        return analysisRepository.findByMockApplyId(mockApplyId)
                .filter(analysis -> inputFingerprint.equals(analysis.getInputFingerprint()))
                .isPresent();
    }

    private MockApply resolveMockApplyBySequence(JobPosting jobPosting, int sequence) {
        if (sequence < 1) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "sequence는 1 이상의 값이어야 합니다."
            );
        }

        List<MockApply> mockApplies = mockApplyRepository.findAllByUserIdAndJobPostingIdOrderByIdAsc(
                jobPosting.getUser().getId(),
                jobPosting.getId()
        );

        int derivedSequence = 0;
        for (MockApply mockApply : mockApplies) {
            derivedSequence++;
            int resolvedSequence = mockApply.getSequence() != null && mockApply.getSequence() > 0
                    ? mockApply.getSequence()
                    : derivedSequence;

            if (resolvedSequence == sequence) {
                return mockApply;
            }
        }

        throw new GeneralException(
                GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                "해당 순번의 모의 서류 지원을 찾을 수 없습니다. jobPostingId="
                        + jobPosting.getId()
                        + ", sequence="
                        + sequence
        );
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

    private MockApply lockOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findByIdForUpdate(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
    }

    private AnalysisResponse reuseExistingAnalysisIfSameInput(User user, Long mockApplyId, String inputFingerprint) {
        return analysisRepository.findByMockApplyId(mockApplyId)
                .filter(analysis -> inputFingerprint.equals(analysis.getInputFingerprint()))
                .map(analysis -> getAnalysis(user, mockApplyId))
                .orElse(null);
    }

    private String analysisCreditReferenceId(Long mockApplyId, String inputFingerprint) {
        return "mockApplyId=" + mockApplyId + ":fingerprint=" + inputFingerprint;
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
