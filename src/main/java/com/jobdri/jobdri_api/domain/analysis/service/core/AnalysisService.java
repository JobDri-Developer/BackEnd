package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
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
// 자소서 분석 실행 흐름을 조율하는 오케스트레이션 서비스다.
public class AnalysisService {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final JobPostingService jobPostingService;
    private final AnalysisAiClient analysisAiClient;
    private final AnalysisCreditService analysisCreditService;
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;
    private final AnalysisPreparationService analysisPreparationService;
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

        String referenceId = analysisCreditService.createSyncReferenceId(mockApplyId, inputFingerprint);
        analysisCreditService.deduct(user, referenceId);

        try {
            AnalysisLlmResponse llmResponse = executeAnalysis(payload);
            return finalizeAnalysis(user, mockApplyId, payload, llmResponse);
        } catch (RuntimeException e) {
            analysisCreditService.refund(user, referenceId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public void validateAnalysisRequest(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        answeredQuestionsOrThrow(questions);
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAnalysisExecution(User user, Long mockApplyId) {
        return analysisPreparationService.prepare(user, mockApplyId).toExecutionPayload();
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAnalysisExecution(
            User user,
            Long mockApplyId,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        return analysisPreparationService.prepare(user, mockApplyId, similarJobPostings).toExecutionPayload();
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
