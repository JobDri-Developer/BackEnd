package com.jobdri.jobdri_api.domain.workerresult.dto;

import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult;

import java.time.LocalDateTime;

public record WorkerTaskResultResponse(
        String taskId,
        String taskType,
        String status,
        String resultPayload,
        int attemptCount,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static WorkerTaskResultResponse from(WorkerTaskResult result) {
        return new WorkerTaskResultResponse(
                result.getTaskId(),
                result.getTaskType().name(),
                result.getStatus().name(),
                result.getResultPayload(),
                result.getAttemptCount(),
                result.getLastError(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }
}
