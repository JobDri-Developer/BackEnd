package com.jobdri.jobdri_api.domain.payment.entity;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING,
    FAILED,
    COMPLETED
}
