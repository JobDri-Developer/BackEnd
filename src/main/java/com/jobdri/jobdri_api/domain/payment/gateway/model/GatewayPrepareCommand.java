package com.jobdri.jobdri_api.domain.payment.gateway.model;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;

public record GatewayPrepareCommand(
        PaymentProviderType provider,
        String orderId,
        String orderName,
        int amount,
        int creditAmount,
        String customerEmail
) {
}
