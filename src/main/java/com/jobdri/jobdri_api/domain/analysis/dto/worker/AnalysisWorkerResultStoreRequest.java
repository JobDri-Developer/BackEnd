package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AnalysisWorkerResultStoreRequest(
        @NotNull Long userId,
        @NotNull Long mockApplyId,
        @Valid @NotNull AnalysisLlmResponse llmResponse
) {
}
