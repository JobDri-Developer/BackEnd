package com.jobdri.jobdri_api.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CouponRedeemRequest(
        @NotBlank(message = "couponCode는 필수입니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9]{12}$",
                message = "couponCode는 영문 또는 숫자 12자리여야 합니다."
        )
        String couponCode
) {
    public CouponRedeemRequest {
        if (couponCode != null) {
            couponCode = couponCode.trim();
        }
    }
}
