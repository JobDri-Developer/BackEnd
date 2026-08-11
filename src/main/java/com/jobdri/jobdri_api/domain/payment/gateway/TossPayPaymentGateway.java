package com.jobdri.jobdri_api.domain.payment.gateway;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayConfirmCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentQuery;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentSnapshot;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareResult;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundResult;
import com.jobdri.jobdri_api.domain.payment.service.TossPayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TossPayPaymentGateway implements PaymentGateway {

    private static final String CURRENCY = "KRW";

    private final TossPayClient tossPayClient;

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.TOSS_PAY_DIRECT;
    }

    @Override
    public GatewayPrepareResult prepare(GatewayPrepareCommand command) {
        var response = tossPayClient.createPayment(
                command.orderId(),
                command.amount(),
                command.orderName()
        );
        return new GatewayPrepareResult(
                type(),
                null,
                response.payToken(),
                response.checkoutPage(),
                null,
                null,
                null,
                CURRENCY
        );
    }

    @Override
    public GatewayPaymentSnapshot confirm(GatewayConfirmCommand command) {
        throw unsupported();
    }

    @Override
    public GatewayPaymentSnapshot fetch(GatewayPaymentQuery query) {
        throw unsupported();
    }

    @Override
    public GatewayRefundResult refund(GatewayRefundCommand command) {
        throw unsupported();
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("TossPayPaymentGateway currently supports prepare only.");
    }
}
