package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerCompleteRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerContextResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.infrastructure.async.AnalysisAsyncWorkerBridge;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisInputFingerprintProvider;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.domain.workerresult.dto.WorkerTaskResultResponse;
import com.jobdri.jobdri_api.domain.workerresult.service.WorkerTaskResultService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
// 외부 분석 워커와 내부 분석 도메인 상태를 연결해 주는 브리지 서비스다.
public class AnalysisWorkerBridgeService {
    private final AnalysisAsyncWorkerBridge analysisAsyncWorkerBridge;

    public AnalysisWorkerBridgeService(
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncTaskRepository analysisAsyncTaskRepository,
            AnalysisService analysisService,
            AnalysisAsyncCreditCoordinator analysisAsyncCreditCoordinator,
            UserService userService,
            WorkerTaskResultService workerTaskResultService,
            AnalysisInputFingerprintProvider analysisInputFingerprintProvider,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.analysisAsyncWorkerBridge = new AnalysisAsyncWorkerBridge(
                analysisAsyncTaskService,
                analysisAsyncTaskRepository,
                analysisService,
                analysisAsyncCreditCoordinator,
                userService,
                workerTaskResultService,
                analysisInputFingerprintProvider,
                objectMapper,
                transactionTemplate
        );
    }

    @Transactional
    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        analysisAsyncWorkerBridge.markRunning(taskId, workerId, retryCount, submittedAt);
    }

    @Transactional
    public void markRetry(
            String taskId,
            AnalysisAsyncFailureReason failureReason,
            String errorMessage,
            int retryCount,
            String workerId,
            Long queueLatencyMillis
    ) {
        analysisAsyncWorkerBridge.markRetry(
                taskId,
                failureReason,
                errorMessage,
                retryCount,
                workerId,
                queueLatencyMillis
        );
    }

    @Transactional
    public void failTask(
            String taskId,
            AnalysisAsyncFailureReason failureReason,
            String errorMessage,
            int retryCount,
            String workerId,
            Long queueLatencyMillis
    ) {
        analysisAsyncWorkerBridge.failTask(
                taskId,
                failureReason,
                errorMessage,
                retryCount,
                workerId,
                queueLatencyMillis
        );
    }

    public AnalysisWorkerContextResponse getContext(String taskId, Long userId, Long mockApplyId) {
        return analysisAsyncWorkerBridge.getContext(taskId, userId, mockApplyId);
    }

    @Transactional
    public AnalysisResponse completeTask(String taskId, AnalysisWorkerCompleteRequest request) {
        return analysisAsyncWorkerBridge.completeTask(taskId, request);
    }

    @Transactional
    public void storeGeneratedResult(String taskId, AnalysisWorkerResultStoreRequest request) {
        analysisAsyncWorkerBridge.storeGeneratedResult(taskId, request);
    }

    @Transactional(readOnly = true)
    public WorkerTaskResultResponse getStoredResult(String taskId) {
        return analysisAsyncWorkerBridge.getStoredResult(taskId);
    }
}
