package com.jobdri.jobdri_api.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuth2TokenExchangeRequest(
        @NotBlank(message = "로그인 코드는 필수입니다.")
        @Size(max = 100, message = "로그인 코드 형식이 올바르지 않습니다.")
        String code
) {
}
