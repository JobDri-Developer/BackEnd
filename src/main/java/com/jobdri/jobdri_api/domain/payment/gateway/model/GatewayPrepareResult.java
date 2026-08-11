package com.jobdri.jobdri_api.domain.payment.gateway.model;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;

public record GatewayPrepareResult(
        PaymentProviderType provider,
        String externalPaymentId,
        String payToken,
        String checkoutPage,
        String redirectUrl,
        String storeId,
        String channelKey,
        String currency
) {
}
