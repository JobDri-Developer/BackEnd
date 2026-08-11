package com.jobdri.jobdri_api.domain.payment.gateway.command;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;

public record GatewayPrepareCommand(
        PaymentProviderType provider,
        String orderId,
        String orderName,
        int amount,
        int creditAmount,
        String customerEmail
) {
}
