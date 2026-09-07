package com.jobdri.jobdri_api.domain.payment.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaymentLogMasking {

    private PaymentLogMasking() {
    }

    public static Map<String, String> paymentContext(
            String orderId,
            Long userId,
            String planCode,
            Integer amount
    ) {
        Map<String, String> context = new LinkedHashMap<>();
        if (orderId != null) {
            context.put("orderId", orderId);
        }
        if (userId != null) {
            context.put("paymentUserId", String.valueOf(userId));
        }
        if (planCode != null) {
            context.put("planCode", planCode);
        }
        if (amount != null) {
            context.put("amount", String.valueOf(amount));
        }
        return context;
    }

    public static Map<String, String> paymentContext(String orderId, Integer amount) {
        return paymentContext(orderId, null, null, amount);
    }
}
