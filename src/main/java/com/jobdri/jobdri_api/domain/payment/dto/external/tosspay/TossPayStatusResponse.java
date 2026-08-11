package com.jobdri.jobdri_api.domain.payment.dto.external.tosspay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayStatusResponse(
        Integer code,
        String errorCode,
        String msg,
        String mode,
        String payToken,
        String orderNo,
        String payStatus,
        String payMethod,
        Integer amount,
        Integer discountedAmount,
        Integer paidAmount
) {
}
