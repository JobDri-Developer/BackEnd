package com.jobdri.jobdri_api.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentPrepareRequest(
        @NotBlank(message = "플랜 코드는 필수입니다.")
        String planCode,
        String provider
) {
    public PaymentPrepareRequest(String planCode) {
        this(planCode, null);
    }
}
