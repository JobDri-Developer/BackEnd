package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import jakarta.validation.constraints.NotNull;

public record MockApplyCreateMockFromJobPostingRequest(
        @NotNull(message = "공고 ID는 필수입니다.")
        Long jobPostingId
) {
}
