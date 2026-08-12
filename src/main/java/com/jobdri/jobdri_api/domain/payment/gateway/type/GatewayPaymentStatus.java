package com.jobdri.jobdri_api.domain.payment.gateway.type;

public enum GatewayPaymentStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    COMPLETED,
    REFUNDED,
    CANCELED,
    FAILED,
    UNKNOWN
}
