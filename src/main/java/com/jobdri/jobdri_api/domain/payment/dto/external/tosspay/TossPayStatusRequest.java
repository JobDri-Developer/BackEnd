package com.jobdri.jobdri_api.domain.payment.dto.external.tosspay;

public record TossPayStatusRequest(
        String apiKey,
        String payToken,
        String orderNo
) {
}
