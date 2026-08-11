package com.jobdri.jobdri_api.domain.payment.gateway.model;

public enum GatewayPaymentStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    COMPLETED,
    CANCELED,
    FAILED,
    UNKNOWN
}
