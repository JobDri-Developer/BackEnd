package com.jobdri.jobdri_api.domain.payment.dto.portone;

public record PortOnePrepareData(
        String storeId,
        String channelKey,
        String redirectUrl
) {
}
