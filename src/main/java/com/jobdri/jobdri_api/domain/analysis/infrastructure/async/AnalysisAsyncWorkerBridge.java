package com.jobdri.jobdri_api.domain.analysis.infrastructure.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerCompleteRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerContextResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.CorpusReferenceContext;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncCreditStatus;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncTaskStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncTaskService;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisInputFingerprintProvider;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.domain.workerresult.dto.WorkerTaskResultResponse;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult.TaskType;
import com.jobdri.jobdri_api.domain.workerresult.service.WorkerTaskResultService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
// 외부 분석 워커와 내부 분석 도메인 상태를 연결한다.
public class AnalysisAsyncWorkerBridge {
    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    private final AnalysisService analysisService;
    private final AnalysisCreditService analysisCreditService;
    private final UserService userService;
    private final WorkerTaskResultService workerTaskResultService;
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;
    private final ObjectMapper objectMapper;

    public AnalysisAsyncWorkerBridge(
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncTaskRepository analysisAsyncTaskRepository,
            AnalysisService analysisService,
            AnalysisCreditService analysisCreditService,
            UserService userService,
            WorkerTaskResultService workerTaskResultService,
            AnalysisInputFingerprintProvider analysisInputFingerprintProvider,
            ObjectMapper objectMapper
    ) {
        this.analysisAsyncTaskService = analysisAsyncTaskService;
        this.analysisAsyncTaskRepository = analysisAsyncTaskRepository;
        this.analysisService = analysisService;
        this.analysisCreditService = analysisCreditService;
        this.userService = userService;
        this.workerTaskResultService = workerTaskResultService;
        this.analysisInputFingerprintProvider = analysisInputFingerprintProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        AnalysisAsyncTask task = getTaskForUpdate(taskId);
        if (isTerminal(task)) {
            return;
        }
        analysisAsyncTaskService.markRunning(taskId, workerId, retryCount, submittedAt);
        try (var ignored = LoggingContext.with("worker.task.running", null, workerContext(taskId, "ANALYSIS", workerId, retryCount, null))) {
            log.info("Analysis worker marked task as running");
        }
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
        AnalysisAsyncTask task = getTaskForUpdate(taskId);
        if (isTerminal(task)) {
            return;
        }
        analysisAsyncTaskService.updateWorkerMetadata(taskId, workerId, queueLatencyMillis);
        analysisAsyncTaskService.markRetryScheduled(taskId, failureReason, errorMessage, retryCount);
        try (var ignored = LoggingContext.with("worker.task.retry", null, workerContext(taskId, "ANALYSIS", workerId, retryCount, queueLatencyMillis))) {
            log.warn("Analysis worker scheduled retry: failureReason={}", failureReason);
        }
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
        AnalysisAsyncTask task = getTaskForUpdate(taskId);
        if (isTerminal(task)) {
            return;
        }

        analysisAsyncTaskService.updateWorkerMetadata(taskId, workerId, queueLatencyMillis);
        releaseCreditIfNeeded(task);
        analysisAsyncTaskService.markFailed(taskId, failureReason, errorMessage, retryCount);
        try (var ignored = LoggingContext.with("worker.task.failed", null, workerContext(taskId, "ANALYSIS", workerId, retryCount, queueLatencyMillis))) {
            log.warn("Analysis worker failed task: failureReason={}", failureReason);
        }
    }

    @Transactional
    public AnalysisWorkerContextResponse getContext(String taskId, Long userId, Long mockApplyId) {
        AnalysisAsyncTask task = getTaskForUpdate(taskId);
        rejectIfCancelled(task, "취소된 자소서 분석 작업입니다. taskId=" + taskId);
        if (!task.getUserId().equals(userId) || !task.getMockApplyId().equals(mockApplyId)) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "자소서 분석 worker 컨텍스트 요청 정보가 작업 정보와 일치하지 않습니다."
            );
        }
        reserveCreditIfNeeded(task);
        if (task.getExecutionContextSnapshot() != null) {
            return readContextSnapshot(task);
        }

        User user = userService.getUser(userId);
        AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(user, mockApplyId);
        AnalysisWorkerContextResponse context = new AnalysisWorkerContextResponse(
                userId,
                mockApplyId,
                payload.jobPosting().getCompany().getName(),
                payload.jobPosting().getDetailClassification().getDetailName(),
                payload.jobPosting().getTask(),
                payload.jobPosting().getRequirement(),
                payload.jobPosting().getPreferred(),
                payload.jobPosting().getDetailClassification().getMiddleClassification().getClassification().getBigName(),
                payload.jobPosting().getDetailClassification().getMiddleClassification().getMiddleName(),
                payload.jobPosting().getDetailClassification().getDetailName(),
                toQuestionItems(payload.questions()),
                CorpusReferenceContext.from(payload.retrievalContext()),
                payload.similarJobPostings()
        );
        task.captureExecutionSnapshot(
                writeContextSnapshot(context),
                analysisInputFingerprintProvider.create(payload)
        );
        return context;
    }

    @Transactional
    public AnalysisResponse completeTask(String taskId, AnalysisWorkerCompleteRequest request) {
        AnalysisAsyncTask task = getTaskForUpdate(taskId);
        if (!task.getUserId().equals(request.userId()) || !task.getMockApplyId().equals(request.mockApplyId())) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "자소서 분석 worker 완료 요청 정보가 작업 정보와 일치하지 않습니다."
            );
        }
        if (task.getStatus() == AnalysisAsyncTaskStatus.CANCELLED) {
            workerTaskResultService.markDeliveryFailedIfPresent(
                    TaskType.ANALYSIS_COMPLETE,
                    taskId,
                    "취소된 자소서 분석 비동기 작업입니다."
            );
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "취소된 자소서 분석 비동기 작업입니다. taskId=" + taskId
            );
        }
        workerTaskResultService.upsertGenerated(
                TaskType.ANALYSIS_COMPLETE,
                taskId,
                new AnalysisWorkerResultStoreRequest(request.userId(), request.mockApplyId(), request.llmResponse())
        );
        if (task.getStatus() == AnalysisAsyncTaskStatus.SUCCEEDED) {
            workerTaskResultService.markDeliveredIfPresent(TaskType.ANALYSIS_COMPLETE, taskId);
            return analysisService.getAnalysis(userService.getUser(request.userId()), request.mockApplyId());
        }
        if (task.getStatus() == AnalysisAsyncTaskStatus.FAILED) {
            if (!task.isRecoverablePublishFailure()) {
                workerTaskResultService.markDeliveryFailedIfPresent(
                        TaskType.ANALYSIS_COMPLETE,
                        taskId,
                        "이미 실패 처리된 자소서 분석 비동기 작업입니다."
                );
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "이미 실패 처리된 자소서 분석 비동기 작업입니다. taskId=" + taskId
                );
            }
        }

        User user = userService.getUser(request.userId());
        AnalysisWorkerContextResponse contextSnapshot = readContextSnapshot(task);
        AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(
                user,
                request.mockApplyId(),
                contextSnapshot.similarJobPostings()
        ).withAnswerSnapshots(contextSnapshot.questions().stream()
                .filter(question -> question.answer() != null && !question.answer().isBlank())
                .map(question -> new AnalysisExecutionPayload.AnswerSnapshot(
                        question.questionId(),
                        question.answer()
                ))
                .toList());
        AnalysisLlmResponse llmResponse = request.llmResponse();
        AnalysisResponse response = analysisService.finalizeAnalysis(
                user,
                request.mockApplyId(),
                payload,
                llmResponse,
                task.getInputFingerprintSnapshot()
        );
        analysisAsyncTaskService.updateWorkerMetadata(taskId, request.workerId(), request.queueLatencyMillis());
        confirmCreditIfNeeded(task);
        analysisAsyncTaskService.markSuccess(taskId, response);
        workerTaskResultService.markDeliveredIfPresent(TaskType.ANALYSIS_COMPLETE, taskId);
        try (var ignored = LoggingContext.with(
                "worker.task.completed",
                null,
                workerContext(taskId, "ANALYSIS", request.workerId(), task.getRetryCount(), request.queueLatencyMillis())
        )) {
            log.info("Analysis worker completed task");
        }
        return response;
    }

    @Transactional
    public void storeGeneratedResult(String taskId, AnalysisWorkerResultStoreRequest request) {
        AnalysisAsyncTask task = getTask(taskId);
        rejectIfCancelled(task, "취소된 자소서 분석 비동기 작업입니다. taskId=" + taskId);
        if (!task.getUserId().equals(request.userId()) || !task.getMockApplyId().equals(request.mockApplyId())) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "자소서 분석 worker 결과 저장 요청 정보가 작업 정보와 일치하지 않습니다."
            );
        }
        workerTaskResultService.upsertGenerated(TaskType.ANALYSIS_COMPLETE, taskId, request);
        try (var ignored = LoggingContext.with("worker.result.stored", null, workerContext(taskId, "ANALYSIS", null, task.getRetryCount(), null))) {
            log.info("Analysis worker result stored");
        }
    }

    @Transactional(readOnly = true)
    public WorkerTaskResultResponse getStoredResult(String taskId) {
        getTask(taskId);
        return workerTaskResultService.get(taskId);
    }

    private List<AnalysisWorkerContextResponse.AnalysisWorkerQuestionItem> toQuestionItems(List<Question> questions) {
        return questions.stream()
                .map(question -> new AnalysisWorkerContextResponse.AnalysisWorkerQuestionItem(
                        question.getId(),
                        question.getContent(),
                        question.getAnswer(),
                        question.getLimit()
                ))
                .toList();
    }

    private String writeContextSnapshot(AnalysisWorkerContextResponse context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "자소서 분석 worker 컨텍스트 snapshot 저장에 실패했습니다.",
                    exception
            );
        }
    }

    private AnalysisWorkerContextResponse readContextSnapshot(AnalysisAsyncTask task) {
        if (task.getExecutionContextSnapshot() == null || task.getInputFingerprintSnapshot() == null) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "자소서 분석 worker 실행 snapshot이 존재하지 않습니다. taskId=" + task.getTaskId()
            );
        }
        try {
            return objectMapper.readValue(task.getExecutionContextSnapshot(), AnalysisWorkerContextResponse.class);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "자소서 분석 worker 컨텍스트 snapshot을 읽을 수 없습니다. taskId=" + task.getTaskId(),
                    exception
            );
        }
    }

    private AnalysisAsyncTask getTask(String taskId) {
        return analysisAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private AnalysisAsyncTask getTaskForUpdate(String taskId) {
        return analysisAsyncTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private boolean isTerminal(AnalysisAsyncTask task) {
        return task.getStatus() == AnalysisAsyncTaskStatus.SUCCEEDED
                || task.getStatus() == AnalysisAsyncTaskStatus.FAILED
                || task.getStatus() == AnalysisAsyncTaskStatus.CANCELLED;
    }

    private void rejectIfCancelled(AnalysisAsyncTask task, String message) {
        if (task.getStatus() == AnalysisAsyncTaskStatus.CANCELLED || task.isCancelRequested()) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, message);
        }
    }

    private void reserveCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != AnalysisAsyncCreditStatus.NONE) {
            return;
        }

        User user = userService.getUser(task.getUserId());
        String creditReferenceId = analysisCreditService.createAsyncReferenceId(task.getTaskId());
        analysisCreditService.deduct(user, creditReferenceId);
        analysisAsyncTaskService.markCreditReserved(task.getTaskId(), creditReferenceId);
    }

    private void confirmCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != AnalysisAsyncCreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return;
        }
        analysisAsyncTaskService.markCreditConfirmed(task.getTaskId());
    }

    private void releaseCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != AnalysisAsyncCreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return;
        }
        User user = userService.getUser(task.getUserId());
        analysisCreditService.refund(user, task.getCreditReferenceId());
        analysisAsyncTaskService.markCreditReleased(task.getTaskId());
    }

    private Map<String, String> workerContext(
            String taskId,
            String taskType,
            String workerId,
            Integer retryCount,
            Long queueLatencyMillis
    ) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(LoggingMdcKeys.TASK_ID, taskId);
        context.put(LoggingMdcKeys.TASK_TYPE, taskType);
        if (workerId != null) {
            context.put(LoggingMdcKeys.WORKER_ID, workerId);
        }
        if (retryCount != null) {
            context.put(LoggingMdcKeys.RETRY_COUNT, String.valueOf(retryCount));
        }
        if (queueLatencyMillis != null) {
            context.put(LoggingMdcKeys.QUEUE_LATENCY_MILLIS, String.valueOf(queueLatencyMillis));
        }
        return context;
    }
}
