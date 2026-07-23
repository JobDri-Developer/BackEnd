package com.jobdri.jobdri_api.domain.payment.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaymentLogMasking {

    private PaymentLogMasking() {
    }

    public static Map<String, String> paymentContext(
            String orderId,
            String paymentKey,
            Long userId,
            String planCode,
            Integer amount
    ) {
        Map<String, String> context = new LinkedHashMap<>();
        if (orderId != null) {
            context.put("orderId", orderId);
        }
        String maskedPaymentKey = maskPaymentKey(paymentKey);
        if (maskedPaymentKey != null) {
            context.put("paymentKey", maskedPaymentKey);
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

    public static Map<String, String> paymentContext(String orderId, String paymentKey, Integer amount) {
        return paymentContext(orderId, paymentKey, null, null, amount);
    }

    public static String maskPaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            return null;
        }
        if (paymentKey.length() <= 10) {
            return "****";
        }
        return paymentKey.substring(0, 6) + "..." + paymentKey.substring(paymentKey.length() - 4);
    }
}
