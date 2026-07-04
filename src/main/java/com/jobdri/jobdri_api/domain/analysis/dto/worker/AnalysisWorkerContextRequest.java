package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalysisWorkerContextRequest(
        @NotBlank String taskId,
        @NotNull Long userId,
        @NotNull Long mockApplyId
) {
}
