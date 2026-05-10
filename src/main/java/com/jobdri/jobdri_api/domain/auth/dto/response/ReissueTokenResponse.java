package com.jobdri.jobdri_api.domain.auth.dto.response;

import lombok.Builder;

@Builder
public record ReissueTokenResponse(
        String accessToken,
        String refreshToken
) {
}
