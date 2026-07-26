package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFinalizeRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerResultStoreRequest;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.domain.workerresult.dto.WorkerTaskResultResponse;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult.TaskType;
import com.jobdri.jobdri_api.domain.workerresult.service.WorkerTaskResultService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingWorkerBridgeService {

    private final JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private final JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;
    private final JobPostingImageStorageService jobPostingImageStorageService;
    private final JobPostingClassificationService jobPostingClassificationService;
    private final JobPostingService jobPostingService;
    private final UserService userService;
    private final WorkerTaskResultService workerTaskResultService;

    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        JobPostingAsyncTask task = getTask(taskId);
        if (isTerminal(task)) {
            return;
        }
        jobPostingAsyncTaskService.markRunning(taskId, workerId, retryCount, submittedAt);
        try (var ignored = LoggingContext.with("worker.task.running", null, workerContext(taskId, "JOB_POSTING_INGEST", workerId, retryCount, null))) {
            log.info("Job posting worker marked task as running");
        }
    }

    @Transactional
    public JobPostingIngestResponse completeTask(String taskId, JobPostingIngestResponse result) {
        // Legacy direct-complete path for older workers. New workers should prefer result -> finalize.
        JobPostingAsyncTask task = getTask(taskId);
        rejectIfCancelled(task, "취소된 채용 공고 비동기 작업입니다. taskId=" + taskId);
        workerTaskResultService.upsertGenerated(TaskType.JOB_POSTING_COMPLETE, taskId, result);
        JobPostingIngestResponse response = jobPostingAsyncTaskService.markSuccess(taskId, result);
        workerTaskResultService.markDeliveredIfPresent(TaskType.JOB_POSTING_COMPLETE, taskId);
        try (var ignored = LoggingContext.with("worker.task.completed", null, workerContext(taskId, "JOB_POSTING_INGEST", null, null, null))) {
            log.info("Job posting worker completed task via legacy complete callback");
        }
        return response;
    }

    @Transactional
    public void markRetry(
            String taskId,
            FailureReason failureReason,
            String errorMessage,
            int retryCount,
            String workerId,
            Long queueLatencyMillis
    ) {
        JobPostingAsyncTask task = getTask(taskId);
        if (isTerminal(task)) {
            return;
        }
        jobPostingAsyncTaskService.updateWorkerMetadata(taskId, workerId, queueLatencyMillis);
        jobPostingAsyncTaskService.markRetryScheduled(taskId, failureReason, errorMessage, retryCount);
        try (var ignored = LoggingContext.with("worker.task.retry", null, workerContext(taskId, "JOB_POSTING_INGEST", workerId, retryCount, queueLatencyMillis))) {
            log.warn("Job posting worker scheduled retry: failureReason={}", failureReason);
        }
    }

    @Transactional
    public void failTask(
            String taskId,
            FailureReason failureReason,
            String errorMessage,
            int retryCount,
            String workerId,
            Long queueLatencyMillis
    ) {
        JobPostingAsyncTask task = getTask(taskId);
        if (isTerminal(task)) {
            return;
        }
        jobPostingAsyncTaskService.updateWorkerMetadata(taskId, workerId, queueLatencyMillis);
        jobPostingAsyncTaskService.markFailed(taskId, failureReason, errorMessage, retryCount);
        try (var ignored = LoggingContext.with("worker.task.failed", null, workerContext(taskId, "JOB_POSTING_INGEST", workerId, retryCount, queueLatencyMillis))) {
            log.warn("Job posting worker failed task: failureReason={}", failureReason);
        }
    }

    public String createReadableImageUrl(Long userId, String imageObjectKey) {
        return jobPostingImageStorageService.createReadableImageUrl(userId, imageObjectKey);
    }

    public List<JobPostingClassificationCandidateResponse> findCandidates(JobPostingExtractResponse extracted) {
        return jobPostingClassificationService.findCandidates(extracted, 5);
    }

    @Transactional
    public JobPostingIngestResponse finalizeAndComplete(
            String taskId,
            Long userId,
            JobPostingExtractResponse extracted,
            List<JobPostingClassificationCandidateResponse> candidates,
            com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse classification,
            JobPostingGenerateResponse generated
    ) {
        JobPostingAsyncTask task = jobPostingAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));

        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            workerTaskResultService.markDeliveredIfPresent(TaskType.JOB_POSTING_FINALIZE, taskId);
            return jobPostingAsyncTaskService.getTask(taskId).getResult();
        }
        if (task.getStatus() == TaskStatus.FAILED || task.getStatus() == TaskStatus.CANCELLED) {
            workerTaskResultService.markDeliveryFailedIfPresent(
                    TaskType.JOB_POSTING_FINALIZE,
                    taskId,
                    task.getStatus() == TaskStatus.CANCELLED
                            ? "취소된 채용 공고 비동기 작업입니다."
                            : "이미 실패 처리된 채용 공고 비동기 작업입니다."
            );
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    task.getStatus() == TaskStatus.CANCELLED
                            ? "취소된 채용 공고 비동기 작업입니다. taskId=" + taskId
                            : "이미 실패 처리된 채용 공고 비동기 작업입니다. taskId=" + taskId
            );
        }
        JobPostingIngestQualityValidator.validateExtracted(extracted);
        JobPostingIngestQualityValidator.validateGenerated(generated);

        workerTaskResultService.upsertGenerated(
                TaskType.JOB_POSTING_FINALIZE,
                taskId,
                new JobPostingWorkerFinalizeRequest(taskId, userId, extracted, candidates, classification, generated)
        );

        var saved = jobPostingService.createJobPosting(
                userService.getUser(userId),
                new JobPostingCreateRequest(
                        JobPostingProfileColor.DEFAULT,
                        generated.postingName(),
                        fallbackCompanyName(extracted.companyName()),
                        null,
                        generated.jobTitle(),
                        classification.detailClassificationId(),
                        generated.task(),
                        generated.requirements(),
                        generated.preferredQualifications()
                )
        );

        JobPostingIngestResponse result = new JobPostingIngestResponse(
                true,
                "채용 공고 추출 및 저장에 성공했습니다.",
                extracted,
                candidates,
                classification,
                generated,
                saved
        );
        JobPostingIngestResponse response = jobPostingAsyncTaskService.markSuccess(taskId, result);
        workerTaskResultService.markDeliveredIfPresent(TaskType.JOB_POSTING_FINALIZE, taskId);
        try (var ignored = LoggingContext.with("worker.task.completed", null, workerContext(taskId, "JOB_POSTING_INGEST", null, task.getRetryCount(), null))) {
            log.info("Job posting worker finalized and completed task");
        }
        return response;
    }

    @Transactional
    public void storeFinalizeResult(String taskId, JobPostingWorkerResultStoreRequest request) {
        JobPostingAsyncTask task = getTask(taskId);
        rejectIfCancelled(task, "취소된 채용 공고 비동기 작업입니다. taskId=" + taskId);
        if (!task.getUserId().equals(request.userId())) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "채용 공고 worker 결과 저장 요청 사용자 정보가 작업 정보와 일치하지 않습니다."
            );
        }
        if (!taskId.equals(request.result().taskId())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "채용 공고 worker 결과 저장 요청 taskId가 경로와 일치하지 않습니다."
            );
        }
        workerTaskResultService.upsertGenerated(TaskType.JOB_POSTING_FINALIZE, taskId, request.result());
        try (var ignored = LoggingContext.with("worker.result.stored", null, workerContext(taskId, "JOB_POSTING_INGEST", null, task.getRetryCount(), null))) {
            log.info("Job posting worker result stored");
        }
    }

    @Transactional(readOnly = true)
    public WorkerTaskResultResponse getStoredResult(String taskId) {
        getTask(taskId);
        return workerTaskResultService.get(taskId);
    }

    private JobPostingAsyncTask getTask(String taskId) {
        return jobPostingAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private boolean isTerminal(JobPostingAsyncTask task) {
        return task.getStatus() == TaskStatus.SUCCEEDED
                || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.CANCELLED;
    }

    private void rejectIfCancelled(JobPostingAsyncTask task, String message) {
        if (task.getStatus() == TaskStatus.CANCELLED || task.isCancelRequested()) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, message);
        }
    }

    private String fallbackCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "미분류 회사";
        }
        return companyName;
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
