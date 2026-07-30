package com.jobdri.jobdri_api.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PortOnePaymentCompleteRequest(
        @NotBlank(message = "paymentId는 필수입니다.")
        String paymentId
) {
}
