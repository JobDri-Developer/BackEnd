package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import lombok.Builder;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AnalysisTaskMessage(
        String messageId,
        String taskType,
        String taskId,
        String requestId,
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
                .requestId(MDC.get(LoggingMdcKeys.REQUEST_ID))
                .userId(userId)
                .mockApplyId(mockApplyId)
                .retryCount(0)
                .maxRetryCount(Math.max(0, maxRetryCount))
                .submittedAt(Instant.now())
                .build();
    }
}
