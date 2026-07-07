package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobPostingWorkerRetryRequest(
        @NotNull FailureReason failureReason,
        @NotBlank String errorMessage,
        @Min(0) int retryCount,
        @NotBlank @Size(max = 100) String workerId,
        Long queueLatencyMillis
) {
}
