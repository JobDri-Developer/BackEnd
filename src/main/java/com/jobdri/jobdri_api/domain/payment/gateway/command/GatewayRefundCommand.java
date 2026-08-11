package com.jobdri.jobdri_api.domain.payment.gateway.command;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;

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
