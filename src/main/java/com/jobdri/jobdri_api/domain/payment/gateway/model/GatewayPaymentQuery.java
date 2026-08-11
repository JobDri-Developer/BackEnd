package com.jobdri.jobdri_api.domain.payment.gateway.model;

public record GatewayPaymentQuery(
        String orderId,
        String externalPaymentId,
        String payToken
) {
}
