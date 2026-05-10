package com.jobdri.jobdri_api.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReissueTokenRequest(
        @NotBlank(message = "액세스 토큰은 필수입니다.")
        String accessToken,

        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
