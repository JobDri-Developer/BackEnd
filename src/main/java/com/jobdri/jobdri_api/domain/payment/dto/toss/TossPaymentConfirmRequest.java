package com.jobdri.jobdri_api.domain.payment.dto.toss;

public record TossPaymentConfirmRequest(
        String paymentKey,
        String orderId,
        int amount
) {
}
