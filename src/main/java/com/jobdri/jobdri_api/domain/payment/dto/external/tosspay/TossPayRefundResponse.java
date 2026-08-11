package com.jobdri.jobdri_api.domain.payment.dto.external.tosspay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayRefundResponse(
        Integer code,
        String errorCode,
        String msg,
        String refundNo,
        String payToken,
        String transactionId,
        String payStatus,
        Integer refundedAmount,
        Integer refundedPaidAmount
) {
    public boolean successful() {
        return code != null && code == 0;
    }
}
