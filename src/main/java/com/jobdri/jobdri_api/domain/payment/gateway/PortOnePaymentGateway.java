package com.jobdri.jobdri_api.domain.payment.gateway;

import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOneCancelResponse;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayConfirmCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentQuery;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentSnapshot;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentStatus;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareResult;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundResult;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundStatus;
import com.jobdri.jobdri_api.domain.payment.service.PortOneClient;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortOnePaymentGateway implements PaymentGateway {

    private final PortOneClient portOneClient;

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.PORTONE;
    }

    @Override
    public GatewayPrepareResult prepare(GatewayPrepareCommand command) {
        var response = portOneClient.prepareData();
        return new GatewayPrepareResult(
                type(),
                command.orderId(),
                null,
                null,
                response.redirectUrl(),
                response.storeId(),
                response.channelKey(),
                "KRW"
        );
    }

    @Override
    public GatewayPaymentSnapshot fetch(GatewayPaymentQuery query) {
        var response = portOneClient.getPayment(query.externalPaymentId());
        return new GatewayPaymentSnapshot(
                type(),
                mapStatus(response.status()),
                response.id(),
                null,
                null,
                response.id(),
                response.transactionId(),
                response.status(),
                response.storeId(),
                response.currency(),
                response.amount() == null ? null : response.amount().total()
        );
    }

    @Override
    public GatewayPaymentSnapshot confirm(GatewayConfirmCommand command) {
        throw unsupported();
    }

    @Override
    public GatewayRefundResult refund(GatewayRefundCommand command) {
        if (isBlank(command.externalPaymentId())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_NOT_REFUNDABLE, "포트원 paymentId가 없는 결제는 환불할 수 없습니다.");
        }
        PortOneCancelResponse response = portOneClient.cancelPayment(
                command.externalPaymentId(),
                command.amount(),
                command.reason()
        );
        validateRefundResponse(response, command.amount());
        return new GatewayRefundResult(type(), GatewayRefundStatus.SUCCEEDED, "CANCELLED");
    }

    private GatewayPaymentStatus mapStatus(String externalStatus) {
        if (externalStatus == null) {
            return GatewayPaymentStatus.UNKNOWN;
        }
        return switch (externalStatus.toUpperCase()) {
            case "READY", "PENDING" -> GatewayPaymentStatus.PENDING;
            case "PAID" -> GatewayPaymentStatus.COMPLETED;
            case "FAILED" -> GatewayPaymentStatus.FAILED;
            case "CANCELLED" -> GatewayPaymentStatus.CANCELED;
            default -> GatewayPaymentStatus.UNKNOWN;
        };
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("PortOnePaymentGateway currently supports prepare, fetch, and refund only.");
    }

    private void validateRefundResponse(PortOneCancelResponse response, int amount) {
        if (response == null
                || response.cancellation() == null
                || isBlank(response.cancellation().id())
                || !"SUCCEEDED".equals(response.cancellation().status())
                || response.cancellation().amount() == null
                || response.cancellation().amount() != amount) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_REFUND_FAILED, "포트원 결제 취소 응답 검증에 실패했습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
