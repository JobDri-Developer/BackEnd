package com.jobdri.jobdri_api.domain.payment.gateway.model;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;

public record GatewayRefundResult(
        PaymentProviderType provider,
        GatewayRefundStatus status,
        String externalStatus
) {
}
