package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;

public record PaymentPrepareResponse(
        Long paymentId,
        String orderId,
        String orderName,
        int amount,
        int creditAmount,
        PaymentProviderType provider,
        String portOneStoreId,
        String portOneChannelKey,
        String currency,
        String redirectUrl,
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
                payment.getProviderOrDefault(),
                null,
                null,
                "KRW",
                null,
                payment.getCheckoutPage(),
                customerEmail
        );
    }

    public static PaymentPrepareResponse portOne(
            Payment payment,
            String customerEmail,
            String storeId,
            String channelKey,
            String redirectUrl
    ) {
        return new PaymentPrepareResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getContent(),
                payment.getPrice(),
                payment.getCreditAmount(),
                PaymentProviderType.PORTONE,
                storeId,
                channelKey,
                "KRW",
                redirectUrl,
                null,
                customerEmail
        );
    }
}
