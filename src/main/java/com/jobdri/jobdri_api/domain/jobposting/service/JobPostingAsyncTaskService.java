package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingProgressStepResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationTargetType;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationType;
import com.jobdri.jobdri_api.domain.notification.service.NotificationService;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator.AsyncTaskProgressStatus;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator.ProgressStepDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobPostingAsyncTaskService {
    private static final int DEFAULT_ESTIMATED_REMAINING_SECONDS = 20;
    private static final List<ProgressStepDefinition> PROGRESS_STEPS = List.of(
            new ProgressStepDefinition("VALIDATING_INPUT", "공고 입력값을 확인하고 있어요"),
            new ProgressStepDefinition("DOWNLOADING_IMAGES", "이미지를 준비하고 있어요"),
            new ProgressStepDefinition("EXTRACTING_CONTENT", "공고 내용을 추출하고 있어요"),
            new ProgressStepDefinition("STRUCTURING_JOB_POSTING", "공고 정보를 정리하고 있어요"),
            new ProgressStepDefinition("SAVING_RESULT", "공고 분석 결과를 저장하고 있어요"),
            new ProgressStepDefinition("COMPLETED", "공고 분석이 완료되었습니다")
    );

    private final JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;
    private final ObjectMapper objectMapper;
    private final JobPostingAsyncSseService jobPostingAsyncSseService;
    private final NotificationService notificationService;
    private final AsyncMetricsRecorder asyncMetricsRecorder;
    private final JobPostingQueueProperties jobPostingQueueProperties;
    private final AsyncProgressCalculator asyncProgressCalculator;

    @Transactional
    public JobPostingAsyncTask createPendingTask(Long userId) {
        return jobPostingAsyncTaskRepository.save(
                JobPostingAsyncTask.pending(userId, jobPostingQueueProperties.getMaxRetryCount())
        );
    }

    @Transactional
    public void deleteTask(String taskId) {
        jobPostingAsyncTaskRepository.deleteById(taskId);
    }

    @Transactional
    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (isTerminal(task)) {
            return;
        }
        task.markRunning(workerId, retryCount, submittedAt);
        if (task.getQueueLatencyMillis() != null) {
            asyncMetricsRecorder.recordQueueWait("jobposting", task.getQueueLatencyMillis());
        }
        publishAfterCommit(toStatusResponse(task));
    }

    @Transactional
    public JobPostingIngestResponse markSuccess(String taskId, JobPostingIngestResponse result) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return deserializeResult(task.getResultPayload());
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 실패 처리된 채용 공고 비동기 작업입니다. taskId=" + taskId
            );
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "취소된 채용 공고 비동기 작업입니다. taskId=" + taskId
            );
        }
        task.markSuccess(serializeResult(result));
        recordProcessingMetric(task, "succeeded");
        publishAfterCommit(toStatusResponse(task));
        createSuccessNotificationSafely(task, result);
        return result;
    }

    @Transactional
    public void markRetryScheduled(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (isTerminal(task)) {
            return;
        }
        recordProcessingMetric(task, "retry");
        task.markRetryScheduled(failureReason, errorMessage, retryCount);
        publishAfterCommit(toStatusResponse(task));
    }

    @Transactional
    public void markFailed(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (isTerminal(task)) {
            return;
        }
        recordProcessingMetric(task, "failed");
        task.markFailed(failureReason, errorMessage, retryCount);
        publishAfterCommit(toStatusResponse(task));
        createFailureNotificationSafely(task);
    }

    @Transactional
    public JobPostingAsyncCancelResponse cancelTask(User user, String taskId) {
        JobPostingAsyncTask task = getOwnedTaskState(user, taskId);
        TaskStatus previousStatus = task.getStatus();
        LocalDateTime previousCancelledAt = task.getCancelledAt();
        task.requestCancel();
        boolean cancelled = task.getStatus() == TaskStatus.CANCELLED;
        boolean newlyCancelled = previousStatus != TaskStatus.CANCELLED && cancelled;
        if (newlyCancelled) {
            recordProcessingMetric(task, "cancelled");
        }
        if (newlyCancelled || previousCancelledAt == null && cancelled) {
            publishAfterCommit(toStatusResponse(task));
        }
        return new JobPostingAsyncCancelResponse(
                task.getTaskId(),
                task.getStatus().name(),
                task.getMessage()
        );
    }

    @Transactional
    public void updateWorkerMetadata(String taskId, String workerId, Long queueLatencyMillis) {
        getTaskState(taskId).updateWorkerMetadata(workerId, queueLatencyMillis);
    }

    @Transactional
    public JobPostingAsyncStatusResponse getTask(String taskId) {
        JobPostingAsyncTask taskState = getTaskState(taskId);
        expireTimedOutTaskIfNeeded(taskState);
        return toStatusResponse(taskState);
    }

    @Transactional
    public JobPostingAsyncStatusResponse getTask(User user, String taskId) {
        if (user.getRole() == UserRole.ADMIN) {
            return getTask(taskId);
        }

        JobPostingAsyncTask taskState = jobPostingAsyncTaskRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
        expireTimedOutTaskIfNeeded(taskState);
        return toStatusResponse(taskState);
    }

    @Transactional
    public int sweepTimedOutTasks() {
        int expiredCount = 0;
        for (JobPostingAsyncTask task : jobPostingAsyncTaskRepository.findByStatusIn(EnumSet.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            if (expireTimedOutTaskIfNeeded(task)) {
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private JobPostingAsyncStatusResponse toStatusResponse(JobPostingAsyncTask taskState) {
        return JobPostingAsyncStatusResponse.builder()
                .taskId(taskState.getTaskId())
                .status(taskState.getStatus().name())
                .message(taskState.getMessage())
                .error(taskState.getError())
                .failureReason(taskState.getFailureReason() != null ? taskState.getFailureReason().name() : null)
                .workerId(taskState.getWorkerId())
                .retryCount(taskState.getRetryCount())
                .maxRetryCount(taskState.getMaxRetryCount())
                .queueLatencyMillis(taskState.getQueueLatencyMillis())
                .createdAt(taskState.getCreatedAt())
                .submittedAt(taskState.getSubmittedAt())
                .lastAttemptAt(taskState.getLastAttemptAt())
                .startedAt(taskState.getStartedAt())
                .completedAt(taskState.getCompletedAt())
                .cancelRequested(taskState.isCancelRequested())
                .cancelledAt(taskState.getCancelledAt())
                .currentStep(resolveCurrentStep(taskState))
                .progressPercent(asyncProgressCalculator.resolveProgressPercent(
                        toProgressStatus(taskState.getStatus()),
                        taskState.getProgressPercent()
                ))
                .estimatedRemainingSeconds(asyncProgressCalculator.resolveEstimatedRemainingSeconds(
                        toProgressStatus(taskState.getStatus()),
                        taskState.getEstimatedRemainingSeconds(),
                        taskState.getStartedAt(),
                        DEFAULT_ESTIMATED_REMAINING_SECONDS
                ))
                .steps(buildSteps(taskState))
                .result(deserializeResult(taskState.getResultPayload()))
                .build();
    }

    private JobPostingAsyncTask getOwnedTaskState(User user, String taskId) {
        if (user.getRole() == UserRole.ADMIN) {
            return getTaskState(taskId);
        }
        return jobPostingAsyncTaskRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private JobPostingAsyncTask getTaskState(String taskId) {
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

    private boolean expireTimedOutTaskIfNeeded(JobPostingAsyncTask task) {
        if (isTerminal(task)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (task.getStatus() == TaskStatus.PENDING
                && isExpired(task.getSubmittedAt(), now, jobPostingQueueProperties.getQueueTimeoutSeconds())) {
            markFailed(
                    task.getTaskId(),
                    FailureReason.QUEUE_TIMEOUT,
                    "채용 공고 작업이 대기열에서 시간 내 처리되지 않았습니다.",
                    task.getRetryCount()
            );
            return true;
        }

        LocalDateTime lastActivityAt = task.getLastAttemptAt() != null ? task.getLastAttemptAt() : task.getStartedAt();
        if (task.getStatus() == TaskStatus.RUNNING
                && isExpired(lastActivityAt, now, jobPostingQueueProperties.getProcessingTimeoutSeconds())) {
            markFailed(
                    task.getTaskId(),
                    FailureReason.WORKER_TIMEOUT,
                    "채용 공고 작업이 처리 제한 시간을 초과했습니다.",
                    task.getRetryCount()
            );
            return true;
        }
        return false;
    }

    private boolean isExpired(LocalDateTime baseTime, LocalDateTime now, long timeoutSeconds) {
        if (baseTime == null || timeoutSeconds <= 0) {
            return false;
        }
        return Duration.between(baseTime, now).getSeconds() >= timeoutSeconds;
    }

    private void recordProcessingMetric(JobPostingAsyncTask task, String outcome) {
        if (task.getStartedAt() == null) {
            return;
        }
        long durationMillis = Math.max(0L, Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis());
        asyncMetricsRecorder.recordProcessing("jobposting", outcome, durationMillis);
    }

    private String resolveCurrentStep(JobPostingAsyncTask task) {
        return asyncProgressCalculator.resolveCurrentStep(
                toProgressStatus(task.getStatus()),
                task.getCurrentStep(),
                "VALIDATING_INPUT"
        );
    }

    private List<JobPostingProgressStepResponse> buildSteps(JobPostingAsyncTask task) {
        return asyncProgressCalculator.buildSteps(
                toProgressStatus(task.getStatus()),
                resolveCurrentStep(task),
                PROGRESS_STEPS,
                step -> new JobPostingProgressStepResponse(step.code(), step.label(), step.status())
        );
    }

    private AsyncTaskProgressStatus toProgressStatus(TaskStatus status) {
        return AsyncTaskProgressStatus.valueOf(status.name());
    }

    private String serializeResult(JobPostingIngestResponse result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "채용 공고 비동기 결과 직렬화에 실패했습니다."
            );
        }
    }

    private JobPostingIngestResponse deserializeResult(String resultPayload) {
        if (resultPayload == null || resultPayload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultPayload, JobPostingIngestResponse.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "채용 공고 비동기 결과 역직렬화에 실패했습니다."
            );
        }
    }

    private void publishAfterCommit(JobPostingAsyncStatusResponse statusResponse) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            jobPostingAsyncSseService.publish(statusResponse);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobPostingAsyncSseService.publish(statusResponse);
            }
        });
    }

    private void createSuccessNotificationSafely(JobPostingAsyncTask task, JobPostingIngestResponse result) {
        try {
            createSuccessNotification(task, result);
        } catch (Exception e) {
            log.warn("채용 공고 완료 알림 생성에 실패했습니다. taskId={}, userId={}", task.getTaskId(), task.getUserId(), e);
        }
    }

    private void createFailureNotificationSafely(JobPostingAsyncTask task) {
        try {
            createFailureNotification(task);
        } catch (Exception e) {
            log.warn(
                    "채용 공고 실패 알림 생성에 실패했습니다. taskId={}, userId={}, error={}",
                    task.getTaskId(),
                    task.getUserId(),
                    task.getError(),
                    e
            );
        }
    }

    private void createSuccessNotification(JobPostingAsyncTask task, JobPostingIngestResponse result) {
        boolean hasSavedJobPosting = result.getSaved() != null && result.getSaved().getJobPostingId() != null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("savedToDatabase", result.isSavedToDatabase());
        payload.put("jobPostingId", result.getSaved() != null ? result.getSaved().getJobPostingId() : null);

        notificationService.createNotification(
                task.getUserId(),
                NotificationType.JOB_POSTING_ASYNC_SUCCEEDED,
                "채용 공고 작업이 완료되었습니다.",
                result.isSavedToDatabase()
                        ? "채용 공고 분석과 저장이 완료되었습니다."
                        : "채용 공고 분석이 완료되었습니다.",
                hasSavedJobPosting
                        ? NotificationTargetType.JOB_POSTING_RESULT
                        : NotificationTargetType.JOB_POSTING_TASK,
                hasSavedJobPosting
                        ? String.valueOf(result.getSaved().getJobPostingId())
                        : task.getTaskId(),
                payload
        );
    }

    private void createFailureNotification(JobPostingAsyncTask task) {
        String userFacingMessage = "채용 공고 작업 처리 중 오류가 발생했습니다.";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("failureReason", task.getFailureReason() != null ? task.getFailureReason().name() : null);
        payload.put("status", task.getStatus().name());

        notificationService.createNotification(
                task.getUserId(),
                NotificationType.JOB_POSTING_ASYNC_FAILED,
                "채용 공고 작업이 실패했습니다.",
                userFacingMessage,
                NotificationTargetType.JOB_POSTING_TASK,
                task.getTaskId(),
                payload
        );
    }

}
