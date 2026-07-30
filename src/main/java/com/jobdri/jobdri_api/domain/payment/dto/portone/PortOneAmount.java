package com.jobdri.jobdri_api.domain.payment.dto.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneAmount(
        Integer total
) {
}
