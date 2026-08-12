package com.jobdri.jobdri_api.domain.payment.gateway.query;

public record GatewayPaymentQuery(
        String orderId,
        String externalPaymentId,
        String payToken
) {
}
