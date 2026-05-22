package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;

public record CreditPlanResponse(
        String planCode,
        String name,
        int creditAmount,
        int price,
        boolean recommended
) {
    public static CreditPlanResponse from(CreditPlan plan) {
        return new CreditPlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getCreditAmount(),
                plan.getPrice(),
                plan.isRecommended()
        );
    }
}
