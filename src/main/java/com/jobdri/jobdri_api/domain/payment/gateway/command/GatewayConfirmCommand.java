package com.jobdri.jobdri_api.domain.payment.gateway.command;

public record GatewayConfirmCommand(
        String orderId,
        String paymentKey,
        int amount
) {
}
