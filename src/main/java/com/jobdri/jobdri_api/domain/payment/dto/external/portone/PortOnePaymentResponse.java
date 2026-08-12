package com.jobdri.jobdri_api.domain.payment.dto.external.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String transactionId,
        String status,
        String storeId,
        String currency,
        PortOneAmount amount
) {
}
