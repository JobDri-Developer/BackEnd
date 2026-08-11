package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.type.PaymentStatus;

public record PaymentRefundResponse(
        Long paymentId,
        String orderId,
        PaymentProviderType provider,
        PaymentStatus status,
        int creditAmount,
        int amount,
        int creditBalance
) {
    public static PaymentRefundResponse of(Payment payment, int creditBalance) {
        return new PaymentRefundResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getProviderOrDefault(),
                payment.getStatus(),
                payment.getCreditAmount(),
                payment.getPrice(),
                creditBalance
        );
    }
}
