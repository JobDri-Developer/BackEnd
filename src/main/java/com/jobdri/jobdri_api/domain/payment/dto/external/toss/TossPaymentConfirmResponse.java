package com.jobdri.jobdri_api.domain.payment.dto.external.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentConfirmResponse(
        String paymentKey,
        String orderId,
        String orderName,
        String status,
        int totalAmount,
        String method,
        TossEasyPayInfo easyPay
) {
}
