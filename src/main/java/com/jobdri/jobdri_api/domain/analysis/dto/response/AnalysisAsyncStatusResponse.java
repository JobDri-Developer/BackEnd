package com.jobdri.jobdri_api.domain.analysis.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

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
        Boolean cancelRequested,
        LocalDateTime cancelledAt,
        String currentStep,
        Integer progressPercent,
        Integer estimatedRemainingSeconds,
        List<AnalysisProgressStepResponse> steps,
        AnalysisResponse result
) {
    public AnalysisAsyncStatusResponse withResult(AnalysisResponse result) {
        return new AnalysisAsyncStatusResponse(
                taskId,
                mockApplyId,
                status,
                message,
                error,
                failureReason,
                workerId,
                retryCount,
                maxRetryCount,
                queueLatencyMillis,
                createdAt,
                submittedAt,
                lastAttemptAt,
                startedAt,
                completedAt,
                cancelRequested,
                cancelledAt,
                currentStep,
                progressPercent,
                estimatedRemainingSeconds,
                steps,
                result
        );
    }
}
