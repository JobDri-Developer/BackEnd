package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;

public record PaymentOrderStatusResponse(
        Long paymentId,
        String orderId,
        PaymentStatus status,
        String tossStatus,
        int amount,
        int creditAmount
) {
    public static PaymentOrderStatusResponse from(Payment payment) {
        return new PaymentOrderStatusResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getTossStatus(),
                payment.getPrice(),
                payment.getCreditAmount()
        );
    }
}
