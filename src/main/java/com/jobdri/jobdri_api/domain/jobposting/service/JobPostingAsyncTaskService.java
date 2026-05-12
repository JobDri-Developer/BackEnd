package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobPostingAsyncTaskService {

    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    public String createPendingTask() {
        String taskId = UUID.randomUUID().toString();
        tasks.put(taskId, TaskState.pending(taskId));
        return taskId;
    }

    public void markRunning(String taskId) {
        TaskState current = getTaskState(taskId);
        current.status = TaskStatus.RUNNING;
        current.message = "채용 공고 비동기 처리를 진행 중입니다.";
        current.startedAt = LocalDateTime.now();
    }

    public void markSuccess(String taskId, JobPostingIngestResponse result) {
        TaskState current = getTaskState(taskId);
        current.status = TaskStatus.SUCCEEDED;
        current.message = "채용 공고 비동기 처리에 성공했습니다.";
        current.result = result;
        current.error = null;
        current.completedAt = LocalDateTime.now();
    }

    public void markFailed(String taskId, String errorMessage) {
        TaskState current = getTaskState(taskId);
        current.status = TaskStatus.FAILED;
        current.message = "채용 공고 비동기 처리에 실패했습니다.";
        current.error = errorMessage;
        current.completedAt = LocalDateTime.now();
    }

    public JobPostingAsyncStatusResponse getTask(String taskId) {
        TaskState taskState = getTaskState(taskId);
        return JobPostingAsyncStatusResponse.builder()
                .taskId(taskState.taskId)
                .status(taskState.status.name())
                .message(taskState.message)
                .error(taskState.error)
                .createdAt(taskState.createdAt)
                .startedAt(taskState.startedAt)
                .completedAt(taskState.completedAt)
                .result(taskState.result)
                .build();
    }

    private TaskState getTaskState(String taskId) {
        TaskState taskState = tasks.get(taskId);
        if (taskState == null) {
            throw new GeneralException(
                    GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                    "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
            );
        }
        return taskState;
    }

    private enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    @Getter
    private static class TaskState {
        private final String taskId;
        private final LocalDateTime createdAt;
        private volatile TaskStatus status;
        private volatile String message;
        private volatile String error;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime completedAt;
        private volatile JobPostingIngestResponse result;

        private TaskState(String taskId, LocalDateTime createdAt, TaskStatus status, String message) {
            this.taskId = taskId;
            this.createdAt = createdAt;
            this.status = status;
            this.message = message;
        }

        private static TaskState pending(String taskId) {
            return new TaskState(
                    taskId,
                    LocalDateTime.now(),
                    TaskStatus.PENDING,
                    "채용 공고 비동기 작업이 접수되었습니다."
            );
        }
    }
}
