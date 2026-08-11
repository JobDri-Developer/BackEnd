package com.jobdri.jobdri_api.domain.payment.dto.external.portone;

public record PortOnePrepareData(
        String storeId,
        String channelKey,
        String redirectUrl
) {
}
