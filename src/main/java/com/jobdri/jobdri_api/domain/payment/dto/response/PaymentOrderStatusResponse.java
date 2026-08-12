package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.type.PaymentStatus;

public record PaymentOrderStatusResponse(
        Long paymentId,
        String orderId,
        PaymentProviderType provider,
        PaymentStatus status,
        String tossStatus,
        String externalStatus,
        int amount,
        int creditAmount
) {
    public static PaymentOrderStatusResponse from(Payment payment) {
        return new PaymentOrderStatusResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getProviderOrDefault(),
                payment.getStatus(),
                payment.getTossStatus(),
                payment.getExternalStatus(),
                payment.getPrice(),
                payment.getCreditAmount()
        );
    }
}
