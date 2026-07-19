package com.jobdri.jobdri_api.domain.jobposting.dto.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record JobPostingWorkerResultStoreRequest(
        @NotNull Long userId,
        @Valid @NotNull JobPostingWorkerFinalizeRequest result
) {
}
