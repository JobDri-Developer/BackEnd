package com.jobdri.jobdri_api.domain.payment.type;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;

public enum PaymentProviderType {
    TOSS_PAY_DIRECT,
    PORTONE;

    public static PaymentProviderType fromNullable(PaymentProviderType provider) {
        return provider == null ? TOSS_PAY_DIRECT : provider;
    }

    public static PaymentProviderType require(String provider) {
        if (provider == null || provider.isBlank()) {
            return TOSS_PAY_DIRECT;
        }
        try {
            return PaymentProviderType.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "지원하지 않는 결제 제공자입니다. provider=" + provider, e);
        }
    }
}
