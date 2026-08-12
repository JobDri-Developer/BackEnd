package com.jobdri.jobdri_api.domain.payment.dto.external.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneCancelResponse(
        PortOneCancellation cancellation
) {
}
