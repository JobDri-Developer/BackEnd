package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;

public record PaymentPrepareResponse(
        Long paymentId,
        String orderId,
        String orderName,
        int amount,
        int creditAmount,
        String checkoutPage,
        String customerEmail
) {
    public static PaymentPrepareResponse of(Payment payment) {
        return of(payment, payment.getUser().getEmail());
    }

    public static PaymentPrepareResponse of(Payment payment, String customerEmail) {
        return new PaymentPrepareResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getContent(),
                payment.getPrice(),
                payment.getCreditAmount(),
                payment.getCheckoutPage(),
                customerEmail
        );
    }
}
