package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record JobPostingIngestTaskMessage(
        String messageId,
        String taskType,
        String taskId,
        Long userId,
        String rawText,
        String imageObjectKey,
        int retryCount,
        Instant submittedAt
) {

    public static JobPostingIngestTaskMessage of(String taskId, JobPostingIngestCommand command) {
        return JobPostingIngestTaskMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .taskType("JOB_POSTING_INGEST")
                .taskId(taskId)
                .userId(command.getUserId())
                .rawText(command.getRawText())
                .imageObjectKey(command.getImageObjectKey())
                .retryCount(0)
                .submittedAt(Instant.now())
                .build();
    }
}
