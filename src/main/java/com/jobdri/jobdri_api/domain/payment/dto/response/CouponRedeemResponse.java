package com.jobdri.jobdri_api.domain.payment.dto.response;

import java.time.LocalDateTime;

public record CouponRedeemResponse(
        String couponCode,
        int creditAmount,
        int creditBalance,
        LocalDateTime redeemedAt
) {
}
