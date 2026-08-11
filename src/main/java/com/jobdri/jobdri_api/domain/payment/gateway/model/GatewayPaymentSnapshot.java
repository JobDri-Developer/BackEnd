package com.jobdri.jobdri_api.domain.payment.gateway.model;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;

public record GatewayPaymentSnapshot(
        PaymentProviderType provider,
        GatewayPaymentStatus status,
        String orderId,
        String paymentKey,
        String payToken,
        String externalPaymentId,
        String externalTransactionId,
        String externalStatus,
        String storeId,
        String currency,
        Integer amount
) {
}
