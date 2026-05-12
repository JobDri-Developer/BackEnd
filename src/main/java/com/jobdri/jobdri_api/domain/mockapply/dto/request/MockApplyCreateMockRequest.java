package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import jakarta.validation.constraints.NotNull;

public record MockApplyCreateMockRequest(
        @NotNull(message = "소분류 ID는 필수입니다.")
        Long detailClassificationId,

        String task,

        String requirement,

        String preferred
) {
}
