package com.jobdri.jobdri_api.domain.payment.gateway.model;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;

public record GatewayRefundCommand(
        Long paymentId,
        PaymentProviderType provider,
        String orderId,
        String externalPaymentId,
        String payToken,
        int amount,
        String reason
) {
}
