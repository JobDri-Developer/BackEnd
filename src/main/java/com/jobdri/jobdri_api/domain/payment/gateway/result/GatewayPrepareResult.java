package com.jobdri.jobdri_api.domain.payment.gateway.result;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;

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
