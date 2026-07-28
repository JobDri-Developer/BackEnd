package com.jobdri.jobdri_api.domain.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayCallbackRequest(
        String status,
        String payToken,
        String orderNo,
        String payMethod,
        int amount,
        int discountedAmount,
        int paidAmount,
        String paidTs,
        String transactionId
) {
}
