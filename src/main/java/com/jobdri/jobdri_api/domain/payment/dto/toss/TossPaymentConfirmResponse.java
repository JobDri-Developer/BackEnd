package com.jobdri.jobdri_api.domain.payment.dto.toss;

public record TossPaymentConfirmResponse(
        String paymentKey,
        String orderId,
        String orderName,
        String status,
        Integer totalAmount,
        String method
) {
}
