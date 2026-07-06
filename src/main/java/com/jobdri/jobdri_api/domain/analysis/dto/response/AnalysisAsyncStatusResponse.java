package com.jobdri.jobdri_api.domain.analysis.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AnalysisAsyncStatusResponse(
        String taskId,
        Long mockApplyId,
        String status,
        String message,
        String error,
        String failureReason,
        String workerId,
        Integer retryCount,
        Integer maxRetryCount,
        Long queueLatencyMillis,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        AnalysisResponse result
) {
}
