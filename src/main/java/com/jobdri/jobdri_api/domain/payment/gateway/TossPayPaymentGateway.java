package com.jobdri.jobdri_api.domain.payment.gateway;

import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayRefundResponse;
import com.jobdri.jobdri_api.domain.payment.entity.TossPayStatus;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayConfirmCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentQuery;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentSnapshot;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareResult;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundResult;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundStatus;
import com.jobdri.jobdri_api.domain.payment.service.TossPayClient;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
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
        if (isBlank(command.payToken())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_NOT_REFUNDABLE, "토스페이 payToken이 없는 결제는 환불할 수 없습니다.");
        }
        String refundNo = refundNo(command.paymentId());
        TossPayRefundResponse response = tossPayClient.refundPayment(
                command.payToken(),
                command.orderId(),
                refundNo,
                command.amount(),
                command.reason()
        );
        validateRefundResponse(response, command, refundNo);
        return new GatewayRefundResult(type(), GatewayRefundStatus.SUCCEEDED, response.payStatus());
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("TossPayPaymentGateway currently supports prepare and refund only.");
    }

    private void validateRefundResponse(
            TossPayRefundResponse response,
            GatewayRefundCommand command,
            String refundNo
    ) {
        if (response == null
                || !response.successful()
                || !refundNo.equals(response.refundNo())
                || !command.payToken().equals(response.payToken())
                || !TossPayStatus.REFUND_SUCCESS.name().equals(response.payStatus())
                || response.refundedAmount() == null
                || response.refundedAmount() != command.amount()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_REFUND_FAILED, "토스페이 환불 응답 검증에 실패했습니다.");
        }
    }

    private String refundNo(Long paymentId) {
        return "jobdri-refund-" + paymentId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
