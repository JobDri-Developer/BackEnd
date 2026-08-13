package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.application.port.AnalysisGenerator;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 자소서 분석 실행 흐름을 조율하는 오케스트레이션 서비스다.
public class AnalysisService {
    private static final ConcurrentMap<Long, ReentrantLock> ANALYSIS_LOCKS = new ConcurrentHashMap<>();

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final JobPostingService jobPostingService;
    private final AnalysisGenerator analysisGenerator;
    private final AnalysisCreditService analysisCreditService;
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;
    private final AnalysisPreparationService analysisPreparationService;
    private final AnalysisResultPersistenceService analysisResultPersistenceService;
    private final ObjectProvider<AnalysisService> selfProvider;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLogEvent(action = "ANALYSIS_RUN", targetType = "MOCK_APPLY", targetId = "#arg1")
    public AnalysisResponse analyze(User user, Long mockApplyId) {
        ReentrantLock analysisLock = ANALYSIS_LOCKS.computeIfAbsent(mockApplyId, ignored -> new ReentrantLock());
        analysisLock.lock();
        try {
            AnalysisService self = selfProvider.getObject();
            AnalysisExecutionPayload payload = prepareAnalysisExecution(user, mockApplyId);
            String inputFingerprint = analysisInputFingerprintProvider.create(payload);
            AnalysisResponse cachedResponse = self.lockAndReuseExistingAnalysis(user, mockApplyId, inputFingerprint);
            if (cachedResponse != null) {
                return cachedResponse;
            }

            String referenceId = analysisCreditService.createSyncReferenceId(mockApplyId, inputFingerprint);
            boolean creditDeducted = false;

            try {
                AnalysisLlmResponse llmResponse = executeAnalysis(payload);
                analysisCreditService.deduct(user, referenceId);
                creditDeducted = true;
                return self.finalizeAnalysis(user, mockApplyId, payload, llmResponse);
            } catch (RuntimeException e) {
                if (creditDeducted) {
                    analysisCreditService.refund(user, referenceId);
                }
                throw e;
            }
        } finally {
            analysisLock.unlock();
            if (!analysisLock.hasQueuedThreads()) {
                ANALYSIS_LOCKS.remove(mockApplyId, analysisLock);
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateAnalysisRequest(User user, Long mockApplyId) {
        analysisPreparationService.prepare(user, mockApplyId);
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAsyncAnalysisExecution(User user, Long mockApplyId) {
        return prepareAnalysisExecution(user, mockApplyId);
    }

    @Transactional(readOnly = true)
    public AnalysisExecutionPayload prepareAsyncAnalysisExecution(
            User user,
            Long mockApplyId,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        return prepareAnalysisExecution(user, mockApplyId, similarJobPostings);
    }

    private AnalysisExecutionPayload prepareAnalysisExecution(User user, Long mockApplyId) {
        return analysisPreparationService.prepare(user, mockApplyId).toExecutionPayload();
    }

    private AnalysisExecutionPayload prepareAnalysisExecution(
            User user,
            Long mockApplyId,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        return analysisPreparationService.prepare(user, mockApplyId, similarJobPostings).toExecutionPayload();
    }

    private AnalysisLlmResponse executeAnalysis(AnalysisExecutionPayload payload) {
        return analysisGenerator.analyze(payload);
    }

    @Transactional
    private AnalysisResponse lockAndReuseExistingAnalysis(User user, Long mockApplyId, String inputFingerprint) {
        MockApply mockApply = lockOwnedMockApply(user, mockApplyId);
        return reuseExistingAnalysisIfSameInput(mockApply, inputFingerprint);
    }

    @Transactional
    public AnalysisResponse completeAsyncAnalysis(
            User user,
            Long mockApplyId,
            AnalysisExecutionPayload payload,
            AnalysisLlmResponse llmResponse,
            String inputFingerprint
    ) {
        return persistAnalysis(user, mockApplyId, payload, llmResponse, inputFingerprint);
    }

    @Transactional
    private AnalysisResponse finalizeAnalysis(
            User user,
            Long mockApplyId,
            AnalysisExecutionPayload payload,
            AnalysisLlmResponse llmResponse
    ) {
        return persistAnalysis(
                user,
                mockApplyId,
                payload,
                llmResponse,
                analysisInputFingerprintProvider.create(payload)
        );
    }

    private AnalysisResponse persistAnalysis(
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

    private AnalysisResponse reuseExistingAnalysisIfSameInput(MockApply mockApply, String inputFingerprint) {
        return analysisRepository.findByMockApplyId(mockApply.getId())
                .filter(analysis -> inputFingerprint.equals(analysis.getInputFingerprint()))
                .map(analysis -> analysisResultPersistenceService.getPersistedAnalysis(mockApply, analysis))
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
