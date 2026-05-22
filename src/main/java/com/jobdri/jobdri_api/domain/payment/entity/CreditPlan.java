package com.jobdri.jobdri_api.domain.payment.entity;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum CreditPlan {
    ONE_TIME("ONE_TIME", "1회권", 1, 2500, false),
    FIVE_TIMES("FIVE_TIMES", "5회권", 5, 11500, true),
    TEN_TIMES("TEN_TIMES", "10회권", 10, 19900, false);

    private final String code;
    private final String name;
    private final int creditAmount;
    private final int price;
    private final boolean recommended;

    CreditPlan(String code, String name, int creditAmount, int price, boolean recommended) {
        this.code = code;
        this.name = name;
        this.creditAmount = creditAmount;
        this.price = price;
        this.recommended = recommended;
    }

    public static CreditPlan from(String code) {
        return Arrays.stream(values())
                .filter(plan -> plan.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "지원하지 않는 크레딧 플랜입니다. planCode=" + code
                ));
    }
}
