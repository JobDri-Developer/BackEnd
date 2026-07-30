package com.jobdri.jobdri_api.domain.payment.dto.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneWebhookPayload(
        String type,
        String timestamp,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String paymentId,
            String storeId,
            String transactionId,
            String cancellationId
    ) {
    }
}
