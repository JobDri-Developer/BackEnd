package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MockApplyUpdateNameRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 최대 100자까지 입력할 수 있습니다.")
        String name
) {
}
