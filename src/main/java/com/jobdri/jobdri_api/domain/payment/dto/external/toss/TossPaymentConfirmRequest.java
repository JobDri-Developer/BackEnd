package com.jobdri.jobdri_api.domain.payment.dto.external.toss;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TossPaymentConfirmRequest(
        @NotBlank
        String paymentKey,
        @NotBlank
        String orderId,
        @Positive
        int amount
) {
}
