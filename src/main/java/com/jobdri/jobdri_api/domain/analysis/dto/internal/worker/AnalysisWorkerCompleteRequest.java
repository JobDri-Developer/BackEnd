package com.jobdri.jobdri_api.domain.analysis.dto.internal.worker;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalysisWorkerCompleteRequest(
        @NotNull Long userId,
        @NotNull Long mockApplyId,
        @Valid @NotNull AnalysisLlmResponse llmResponse,
        @NotBlank String workerId,
        Long queueLatencyMillis
) {
}
