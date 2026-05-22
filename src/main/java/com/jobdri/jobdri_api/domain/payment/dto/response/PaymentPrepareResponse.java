package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;

import java.util.Objects;

public record PaymentPrepareResponse(
        Long paymentId,
        String orderId,
        String orderName,
        int amount,
        int creditAmount,
        String clientKey,
        String customerEmail
) {
    public static PaymentPrepareResponse of(Payment payment, String clientKey) {
        Objects.requireNonNull(payment.getUser(), "Payment.user must not be null");
        return new PaymentPrepareResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getContent(),
                payment.getPrice(),
                payment.getCreditAmount(),
                clientKey,
                payment.getUser().getEmail()
        );
    }
}
