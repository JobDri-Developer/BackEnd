package com.jobdri.jobdri_api.domain.payment.dto.external.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossEasyPayInfo(
        String provider
) {
}
