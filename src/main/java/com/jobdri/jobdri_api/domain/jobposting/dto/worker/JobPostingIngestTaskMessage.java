package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import lombok.Builder;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

@Builder
public record JobPostingIngestTaskMessage(
        String messageId,
        String taskType,
        String taskId,
        String requestId,
        Long userId,
        String rawText,
        String imageObjectKey,
        int retryCount,
        int maxRetryCount,
        Instant submittedAt
) {

    public static JobPostingIngestTaskMessage of(String taskId, JobPostingIngestCommand command, int maxRetryCount) {
        return JobPostingIngestTaskMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .taskType("JOB_POSTING_INGEST")
                .taskId(taskId)
                .requestId(MDC.get(LoggingMdcKeys.REQUEST_ID))
                .userId(command.getUserId())
                .rawText(command.getRawText())
                .imageObjectKey(command.getImageObjectKey())
                .retryCount(0)
                .maxRetryCount(Math.max(0, maxRetryCount))
                .submittedAt(Instant.now())
                .build();
    }
}
