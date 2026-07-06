package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record JobPostingWorkerRunningRequest(
        @NotBlank String workerId,
        @Min(0) int retryCount,
        Instant submittedAt
) {
}
