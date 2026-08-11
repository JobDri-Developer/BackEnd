package com.jobdri.jobdri_api.domain.payment.gateway.model;

public record GatewayConfirmCommand(
        String orderId,
        String paymentKey,
        int amount
) {
}
