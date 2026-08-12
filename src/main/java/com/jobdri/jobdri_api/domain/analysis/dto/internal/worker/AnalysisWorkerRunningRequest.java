package com.jobdri.jobdri_api.domain.analysis.dto.internal.worker;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record AnalysisWorkerRunningRequest(
        @NotBlank String workerId,
        @Min(0) int retryCount,
        Instant submittedAt
) {
}
