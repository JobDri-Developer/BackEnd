package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.NotNull;

public record JobPostingMockGenerateRequest(
        @NotNull(message = "중분류 ID는 필수입니다.")
        Long middleClassificationId,

        @NotNull(message = "소분류 ID는 필수입니다.")
        Long detailClassificationId
) {
}
