package com.jobdri.jobdri_api.domain.payment.dto.external.tosspay;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossPayRefundRequest(
        String apiKey,
        String payToken,
        String orderNo,
        String refundNo,
        String reason,
        Integer amount,
        Boolean idempotent
) {
}
