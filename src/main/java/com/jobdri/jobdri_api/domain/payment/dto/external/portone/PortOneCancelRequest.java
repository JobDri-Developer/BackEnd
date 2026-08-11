package com.jobdri.jobdri_api.domain.payment.dto.external.portone;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortOneCancelRequest(
        String storeId,
        Integer amount,
        String reason
) {
}
