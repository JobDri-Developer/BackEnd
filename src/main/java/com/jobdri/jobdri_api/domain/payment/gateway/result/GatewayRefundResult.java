package com.jobdri.jobdri_api.domain.payment.gateway.result;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.type.GatewayRefundStatus;

public record GatewayRefundResult(
        PaymentProviderType provider,
        GatewayRefundStatus status,
        String externalStatus
) {
}
