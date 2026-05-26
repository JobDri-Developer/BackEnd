package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MockApplyCreateActualRequest(
        @NotNull(message = "공고 ID는 필수입니다.")
        Long jobPostingId,

        @Positive(message = "지원 순번은 1 이상이어야 합니다.")
        Integer sequence
) {
    public MockApplyCreateActualRequest(Long jobPostingId) {
        this(jobPostingId, null);
    }
}
