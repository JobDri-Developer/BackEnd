package com.jobdri.jobdri_api.domain.payment.entity;

public enum PortOnePaymentStatus {
    READY,
    PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    PAID,
    FAILED,
    CANCELLED,
    PARTIAL_CANCELLED,
    PAY_PENDING,
    CANCEL_PENDING,
    UNKNOWN;

    public static PortOnePaymentStatus from(String status) {
        if (status == null || status.isBlank()) {
            return UNKNOWN;
        }
        try {
            return PortOnePaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
