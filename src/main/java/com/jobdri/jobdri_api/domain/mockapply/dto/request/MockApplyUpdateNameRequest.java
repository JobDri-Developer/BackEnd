package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MockApplyUpdateNameRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
}
