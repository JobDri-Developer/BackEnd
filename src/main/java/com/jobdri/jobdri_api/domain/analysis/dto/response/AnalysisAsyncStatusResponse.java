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
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        AnalysisResponse result
) {
}
