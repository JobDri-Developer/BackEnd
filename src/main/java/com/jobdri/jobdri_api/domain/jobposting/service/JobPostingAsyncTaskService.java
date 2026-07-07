package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobPostingAsyncTaskService {

    private final JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;
    private final ObjectMapper objectMapper;
    private final JobPostingAsyncSseService jobPostingAsyncSseService;

    @Value("${app.worker.job-posting.max-retry-count:3}")
    private int maxRetryCount;

    @Value("${app.worker.job-posting.queue-timeout-minutes:10}")
    private long queueTimeoutMinutes;

    @Value("${app.worker.job-posting.processing-timeout-minutes:20}")
    private long processingTimeoutMinutes;

    @Transactional
    public JobPostingAsyncTask createPendingTask(Long userId) {
        return jobPostingAsyncTaskRepository.save(JobPostingAsyncTask.pending(userId, maxRetryCount));
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
        jobPostingAsyncSseService.publish(toStatusResponse(task));
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
        task.markSuccess(serializeResult(result));
        jobPostingAsyncSseService.publish(toStatusResponse(task));
        return result;
    }

    @Transactional
    public void markRetryScheduled(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (isTerminal(task)) {
            return;
        }
        task.markRetryScheduled(failureReason, errorMessage, retryCount);
        jobPostingAsyncSseService.publish(toStatusResponse(task));
    }

    @Transactional
    public void markFailed(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return;
        }
        task.markFailed(failureReason, errorMessage, retryCount);
        jobPostingAsyncSseService.publish(toStatusResponse(task));
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
                .result(deserializeResult(taskState.getResultPayload()))
                .build();
    }

    private JobPostingAsyncTask getTaskState(String taskId) {
        return jobPostingAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private boolean isTerminal(JobPostingAsyncTask task) {
        return task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED;
    }

    private void expireTimedOutTaskIfNeeded(JobPostingAsyncTask task) {
        if (isTerminal(task)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (task.getStatus() == TaskStatus.PENDING
                && isExpired(task.getSubmittedAt(), now, queueTimeoutMinutes)) {
            task.markFailed(
                    FailureReason.QUEUE_TIMEOUT,
                    "채용 공고 작업이 대기열에서 시간 내 처리되지 않았습니다.",
                    task.getRetryCount()
            );
            return;
        }

        LocalDateTime lastActivityAt = task.getLastAttemptAt() != null ? task.getLastAttemptAt() : task.getStartedAt();
        if (task.getStatus() == TaskStatus.RUNNING
                && isExpired(lastActivityAt, now, processingTimeoutMinutes)) {
            task.markFailed(
                    FailureReason.WORKER_TIMEOUT,
                    "채용 공고 작업이 처리 제한 시간을 초과했습니다.",
                    task.getRetryCount()
            );
        }
    }

    private boolean isExpired(LocalDateTime baseTime, LocalDateTime now, long timeoutMinutes) {
        if (baseTime == null || timeoutMinutes <= 0) {
            return false;
        }
        return Duration.between(baseTime, now).toMinutes() >= timeoutMinutes;
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
}
