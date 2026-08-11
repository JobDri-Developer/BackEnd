package com.jobdri.jobdri_api.domain.payment.type;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    UNKNOWN,
    FAILED,
    COMPLETED,
    REFUNDED
}
