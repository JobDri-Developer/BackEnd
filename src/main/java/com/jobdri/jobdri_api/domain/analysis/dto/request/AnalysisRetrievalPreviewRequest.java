package com.jobdri.jobdri_api.domain.analysis.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AnalysisRetrievalPreviewRequest(
        @NotNull(message = "mockApplyId는 필수입니다.")
        @Positive(message = "mockApplyId는 1 이상이어야 합니다.")
        Long mockApplyId
) {
}
