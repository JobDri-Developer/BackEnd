package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import jakarta.validation.constraints.NotBlank;

public record JobPostingWorkerFailureRequest(
        @NotBlank String errorMessage
) {
}
