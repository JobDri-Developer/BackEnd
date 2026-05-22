package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;

public record PaymentConfirmResponse(
        Long paymentId,
        String orderId,
        String paymentKey,
        PaymentStatus status,
        int creditAmount,
        int amount,
        int creditBalance
) {
    public static PaymentConfirmResponse of(Payment payment, int creditBalance) {
        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPaymentKey(),
                payment.getStatus(),
                payment.getCreditAmount(),
                payment.getPrice(),
                creditBalance
        );
    }
}
