package com.jobdri.jobdri_api.domain.payment.gateway.result;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.type.GatewayPaymentStatus;

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
