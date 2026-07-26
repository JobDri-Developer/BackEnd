package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

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
    private Boolean cancelRequested;
    private LocalDateTime cancelledAt;
    private String currentStep;
    private Integer progressPercent;
    private Integer estimatedRemainingSeconds;
    private List<JobPostingProgressStepResponse> steps;
    private JobPostingIngestResponse result;
}
