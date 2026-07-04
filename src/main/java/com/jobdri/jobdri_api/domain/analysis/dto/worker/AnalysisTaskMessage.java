package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AnalysisTaskMessage(
        String messageId,
        String taskType,
        String taskId,
        Long userId,
        Long mockApplyId,
        int retryCount,
        int maxRetryCount,
        Instant submittedAt
) {

    public static AnalysisTaskMessage of(
            String taskId,
            Long userId,
            Long mockApplyId,
            int maxRetryCount
    ) {
        return AnalysisTaskMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .taskType("ANALYSIS")
                .taskId(taskId)
                .userId(userId)
                .mockApplyId(mockApplyId)
                .retryCount(0)
                .maxRetryCount(Math.max(0, maxRetryCount))
                .submittedAt(Instant.now())
                .build();
    }
}
