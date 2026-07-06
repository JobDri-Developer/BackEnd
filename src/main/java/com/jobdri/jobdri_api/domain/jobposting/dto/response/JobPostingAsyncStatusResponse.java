package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class JobPostingAsyncStatusResponse {

    private String taskId;
    private String status;
    private String message;
    private String error;
    private String failureReason;
    private String workerId;
    private Integer retryCount;
    private Integer maxRetryCount;
    private Long queueLatencyMillis;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private JobPostingIngestResponse result;
}
