package com.jobdri.jobdri_api.domain.analysis.dto.internal.worker;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalysisWorkerRetryRequest(
        @NotBlank String errorMessage,
        @NotNull FailureReason failureReason,
        @Min(0) int retryCount,
        @NotBlank String workerId,
        Long queueLatencyMillis
) {
}
