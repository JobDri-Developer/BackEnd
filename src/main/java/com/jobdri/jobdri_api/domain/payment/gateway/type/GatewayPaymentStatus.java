package com.jobdri.jobdri_api.domain.payment.gateway.type;

public enum GatewayPaymentStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    COMPLETED,
    CANCELED,
    FAILED,
    UNKNOWN
}
