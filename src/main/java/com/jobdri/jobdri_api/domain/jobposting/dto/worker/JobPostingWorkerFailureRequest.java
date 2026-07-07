package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobPostingWorkerFailureRequest(
        @NotNull FailureReason failureReason,
        @NotBlank String errorMessage,
        @Min(0) int retryCount,
        @NotBlank String workerId,
        Long queueLatencyMillis
) {
}
