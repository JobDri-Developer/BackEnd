package com.jobdri.jobdri_api.domain.payment.dto.tosspay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayCreateResponse(
        Integer code,
        String errorCode,
        String msg,
        Integer status,
        String payToken,
        String checkoutPage
) {
    public boolean successful() {
        return code != null && code == 0 && payToken != null && !payToken.isBlank()
                && checkoutPage != null && !checkoutPage.isBlank();
    }
}
