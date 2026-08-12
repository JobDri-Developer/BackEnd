package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.external.portone.PortOnePaymentResponse;
import com.jobdri.jobdri_api.domain.payment.gateway.PortOnePaymentGateway;
import com.jobdri.jobdri_api.domain.payment.gateway.type.GatewayPaymentStatus;
import com.jobdri.jobdri_api.domain.payment.gateway.result.GatewayPaymentSnapshot;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentOrderStatusResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentRefundResponse;
import com.jobdri.jobdri_api.domain.payment.type.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.type.PaymentStatus;
import com.jobdri.jobdri_api.domain.payment.type.TossPayStatus;
import com.jobdri.jobdri_api.domain.payment.repository.PaymentRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final CreditService creditService;

    @Transactional
    public Payment createPendingPayment(Long userId, CreditPlan plan, String orderId) {
        return createPendingPayment(userId, plan, orderId, PaymentProviderType.TOSS_PAY_DIRECT);
    }

    @Transactional
    public Payment createPendingPayment(Long userId, CreditPlan plan, String orderId, PaymentProviderType provider) {
        User user = userService.getUser(userId);
        return paymentRepository.save(Payment.createPending(
                user,
                "JobDri 크레딧 " + plan.getName(),
                orderId,
                plan.getCode(),
                plan.getCreditAmount(),
                plan.getPrice(),
                provider
        ));
    }

    @Transactional
    public Payment createPortOnePendingPayment(Long userId, CreditPlan plan, String orderId) {
        Payment payment = createPendingPayment(userId, plan, orderId, PaymentProviderType.PORTONE);
        payment.attachPortOnePayment(orderId);
        return payment;
    }

    @Transactional
    public Payment completeTossPayCreation(Long userId, String orderId, String payToken, String checkoutPage) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        payment.attachTossPayPayment(payToken, checkoutPage);
        return payment;
    }

    @Transactional
    public void failTossPayCreation(Long userId, String orderId) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.fail();
        }
    }

    @Transactional
    public void markTossPayCreationUnknown(Long userId, String orderId) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markTossPayUnknown();
        }
    }

    @Transactional
    public void markTossPayCreationUnknown(Long userId, String orderId, String payToken, String checkoutPage) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markTossPayCreationUnknown(payToken, checkoutPage);
        }
    }

    @Transactional
    public PaymentConfirmationStart startConfirmation(Long userId, PaymentConfirmRequest request) {
        Payment payment = getOwnedPaymentForUpdate(userId, request.orderId());
        validateAmount(payment, request.amount());

        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUNDED) {
            if (!payment.hasPaymentKey(request.paymentKey())) {
                throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 처리된 결제입니다.");
            }
            return new PaymentConfirmationStart(payment, true);
        }
        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            if (!payment.hasPaymentKey(request.paymentKey())) {
                throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 처리된 결제입니다.");
            }
            payment.markProcessing(request.paymentKey());
            return new PaymentConfirmationStart(payment, false);
        }
        if (payment.getStatus() == PaymentStatus.PROCESSING || payment.getStatus() == PaymentStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 처리된 결제입니다.");
        }

        payment.markProcessing(request.paymentKey());
        return new PaymentConfirmationStart(payment, false);
    }

    @Transactional
    public PaymentConfirmResponse completeConfirmation(Long userId, PaymentConfirmRequest request) {
        Payment payment = getOwnedPaymentForUpdate(userId, request.orderId());
        validateAmount(payment, request.amount());
        if (!payment.hasPaymentKey(request.paymentKey())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "결제 승인 정보가 일치하지 않습니다.");
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentConfirmResponse.of(payment, userService.getUser(userId).getCredit());
        }
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 처리된 결제입니다.");
        }

        User user = userService.getUser(userId);
        payment.complete(request.paymentKey());
        int creditBalance = creditService.charge(
                user,
                payment.getCreditAmount(),
                payment.getContent(),
                payment.getOrderId()
        );
        return PaymentConfirmResponse.of(payment, creditBalance);
    }

    @Transactional
    public PaymentRefundStart startRefund(Long paymentId, String reason) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. paymentId=" + paymentId
                ));
        User user = userService.getUser(payment.getUser().getId());
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentRefundStart.alreadyRefunded(payment, user.getCredit());
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_NOT_REFUNDABLE, "완료된 결제만 환불할 수 있습니다.");
        }
        if (payment.isRefundInProgress()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "환불 처리가 진행 중입니다.");
        }
        if (user.getCredit() < payment.getCreditAmount()) {
            throw new GeneralException(GeneralErrorCode.INSUFFICIENT_CREDIT, "환불할 크레딧 잔액이 부족합니다.");
        }
        payment.startRefund(reason);
        return PaymentRefundStart.pending(payment, user.getCredit());
    }

    @Transactional
    public PaymentRefundResponse completeRefund(
            Long paymentId,
            PaymentProviderType provider,
            String externalStatus,
            String reason
    ) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. paymentId=" + paymentId
                ));
        User user = userService.getUser(payment.getUser().getId());
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentRefundResponse.of(payment, user.getCredit());
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_NOT_REFUNDABLE, "완료된 결제만 환불할 수 있습니다.");
        }
        if (!payment.isProvider(provider)) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_NOT_REFUNDABLE, "환불 요청 결제수단이 일치하지 않습니다.");
        }
        payment.refundByProviderStatus(externalStatus, reason);
        int creditBalance = creditService.use(
                user,
                payment.getCreditAmount(),
                "결제 환불 크레딧 회수",
                refundCreditReferenceId(payment)
        );
        return PaymentRefundResponse.of(payment, creditBalance);
    }

    @Transactional
    public void markRefundRetryable(Long paymentId, String reason) {
        paymentRepository.findByIdForUpdate(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.COMPLETED
                    && reason.equals(payment.getRefundReason())) {
                payment.clearRefundReason();
            }
        });
    }

    @Transactional
    public PaymentConfirmResponse applyTossPayStatus(String orderId, String payToken, TossPayStatus tossPayStatus, int amount) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderId=" + orderId
                ));
        validateTossPayStatus(payment, payToken, amount);
        GatewayPaymentStatus normalizedStatus = normalizeTossPayStatus(tossPayStatus);

        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "처리할 수 없는 결제 상태입니다.");
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED
                && normalizedStatus != GatewayPaymentStatus.CANCELED
                && normalizedStatus != GatewayPaymentStatus.REFUNDED) {
            return currentConfirmResponse(payment, userService.getUser(payment.getUser().getId()));
        }

        return applyGatewayPaymentTransition(
                payment,
                normalizedStatus,
                tossPayStatus.name(),
                null,
                chargeReferenceId(payment)
        );
    }

    @Transactional
    public PaymentConfirmResponse applyPortOnePayment(Long userId, PortOnePaymentResponse response, String expectedStoreId) {
        return applyPortOnePayment(userId, response, expectedStoreId, true);
    }

    @Transactional
    public PaymentConfirmResponse applyPortOnePayment(Long userId, GatewayPaymentSnapshot snapshot, String expectedStoreId) {
        return applyPortOnePayment(userId, snapshot, expectedStoreId, true);
    }

    @Transactional
    public PaymentConfirmResponse applyPortOneWebhookPayment(PortOnePaymentResponse response, String expectedStoreId) {
        return applyPortOnePayment(null, response, expectedStoreId, false);
    }

    private PaymentConfirmResponse applyPortOnePayment(
            Long userId,
            PortOnePaymentResponse response,
            String expectedStoreId,
            boolean requireOwner
    ) {
        validatePortOneResponseMinimum(response);
        return applyPortOnePayment(userId, toGatewaySnapshot(response), expectedStoreId, requireOwner);
    }

    private PaymentConfirmResponse applyPortOnePayment(
            Long userId,
            GatewayPaymentSnapshot snapshot,
            String expectedStoreId,
            boolean requireOwner
    ) {
        validatePortOneSnapshotMinimum(snapshot);
        Payment payment = paymentRepository.findByOrderIdForUpdate(snapshot.orderId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. paymentId=" + snapshot.orderId()
                ));
        if (requireOwner && !payment.belongsTo(userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 결제에 접근할 수 없습니다.");
        }
        validatePortOnePayment(payment, snapshot, expectedStoreId);
        return applyGatewayPaymentTransition(
                payment,
                snapshot.status(),
                snapshot.externalStatus(),
                snapshot.externalTransactionId(),
                chargeReferenceId(payment)
        );
    }

    @Transactional
    public void markTossPayStatusChecked(String orderId, String payToken, TossPayStatus tossPayStatus, int amount) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderId=" + orderId
                ));
        validateTossPayStatus(payment, payToken, amount);
        payment.markStatusChecked(tossPayStatus.name());
    }

    public PaymentOrderStatusResponse getOwnedOrderStatus(Long userId, String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderId=" + orderId
                ));
        if (!payment.belongsTo(userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 결제에 접근할 수 없습니다.");
        }
        return PaymentOrderStatusResponse.from(payment);
    }

    @Transactional
    public void failConfirmation(Long userId, String orderId, String paymentKey) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        if (!payment.hasPaymentKey(paymentKey)) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.fail();
        }
    }

    @Transactional
    public void markConfirmationUnknown(Long userId, String orderId, String paymentKey) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        if (!payment.hasPaymentKey(paymentKey)) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.markUnknown();
        }
    }

    private Payment getOwnedPaymentForUpdate(Long userId, String orderId) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderId=" + orderId
                ));
        if (!payment.belongsTo(userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 결제에 접근할 수 없습니다.");
        }
        return payment;
    }

    private void validateAmount(Payment payment, int amount) {
        if (payment.getPrice() != amount) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH, "결제 금액이 일치하지 않습니다.");
        }
    }

    private void validateTossPayStatus(Payment payment, String payToken, int amount) {
        if (!payment.hasPayToken(payToken)) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 결제 토큰이 일치하지 않습니다.");
        }
        if (payment.getPrice() != amount) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH, "결제 금액이 일치하지 않습니다.");
        }
    }

    private void validatePortOneResponseMinimum(PortOnePaymentResponse response) {
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 조회 응답 검증에 실패했습니다.");
        }
    }

    private void validatePortOneSnapshotMinimum(GatewayPaymentSnapshot snapshot) {
        if (snapshot == null || snapshot.orderId() == null || snapshot.orderId().isBlank()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 조회 응답 검증에 실패했습니다.");
        }
    }

    private PaymentConfirmResponse applyGatewayPaymentTransition(
            Payment payment,
            GatewayPaymentStatus normalizedStatus,
            String providerStatus,
            String providerTransactionId,
            String chargeReferenceId
    ) {
        User user = userService.getUser(payment.getUser().getId());

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return currentConfirmResponse(payment, user);
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED
                && normalizedStatus != GatewayPaymentStatus.REFUNDED) {
            payment.updateProviderStatus(providerStatus, providerTransactionId);
            return currentConfirmResponse(payment, user);
        }

        return switch (normalizedStatus) {
            case COMPLETED -> PaymentConfirmResponse.of(
                    payment,
                    completeAndCharge(user, payment, providerStatus, providerTransactionId, chargeReferenceId)
            );
            case REFUNDED -> PaymentConfirmResponse.of(
                    payment,
                    refundAndRecover(user, payment, providerStatus)
            );
            case FAILED, CANCELED -> {
                payment.failByProviderStatus(providerStatus, providerTransactionId);
                yield currentConfirmResponse(payment, user);
            }
            case UNKNOWN -> {
                payment.markUnknownByProviderStatus(providerStatus, providerTransactionId);
                yield currentConfirmResponse(payment, user);
            }
            default -> {
                payment.updateProviderStatus(providerStatus, providerTransactionId);
                yield currentConfirmResponse(payment, user);
            }
        };
    }

    private int completeAndCharge(
            User user,
            Payment payment,
            String providerStatus,
            String providerTransactionId,
            String chargeReferenceId
    ) {
        payment.completeByProviderStatus(providerStatus, providerTransactionId);
        return creditService.charge(
                user,
                payment.getCreditAmount(),
                payment.getContent(),
                chargeReferenceId
        );
    }

    private int refundAndRecover(User user, Payment payment, String providerStatus) {
        payment.refundByProviderStatus(providerStatus, null);
        return creditService.use(
                user,
                payment.getCreditAmount(),
                "결제 환불 크레딧 회수",
                refundCreditReferenceId(payment)
        );
    }

    private PaymentConfirmResponse currentConfirmResponse(Payment payment, User user) {
        return PaymentConfirmResponse.of(payment, user.getCredit());
    }

    private String chargeReferenceId(Payment payment) {
        if (payment.isProvider(PaymentProviderType.PORTONE)) {
            return "PAYMENT:PORTONE:" + payment.getOrderId();
        }
        return payment.getOrderId();
    }

    private GatewayPaymentSnapshot toGatewaySnapshot(PortOnePaymentResponse response) {
        return new GatewayPaymentSnapshot(
                PaymentProviderType.PORTONE,
                PortOnePaymentGateway.mapStatus(response.status()),
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

    private GatewayPaymentStatus normalizeTossPayStatus(TossPayStatus tossPayStatus) {
        if (tossPayStatus == null) {
            return GatewayPaymentStatus.UNKNOWN;
        }
        return switch (tossPayStatus) {
            case PAY_COMPLETE -> GatewayPaymentStatus.COMPLETED;
            case PAY_CANCEL -> GatewayPaymentStatus.CANCELED;
            case PAY_APPROVED -> GatewayPaymentStatus.APPROVED;
            case REFUND_SUCCESS -> GatewayPaymentStatus.REFUNDED;
        };
    }

    private void validatePortOnePayment(Payment payment, PortOnePaymentResponse response, String expectedStoreId) {
        if (!payment.isProvider(PaymentProviderType.PORTONE)) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 건이 아닙니다.");
        }
        if (payment.getExternalPaymentId() == null || !payment.getExternalPaymentId().equals(response.id())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 paymentId가 일치하지 않습니다.");
        }
        if (expectedStoreId != null && !expectedStoreId.isBlank() && !expectedStoreId.equals(response.storeId())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 storeId가 일치하지 않습니다.");
        }
        if (!"KRW".equals(response.currency())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "지원하지 않는 포트원 결제 통화입니다.");
        }
        if (response.amount() == null || response.amount().total() == null) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 금액이 누락되었습니다.");
        }
        if (payment.getPrice() != response.amount().total()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH, "결제 금액이 일치하지 않습니다.");
        }
    }

    private void validatePortOnePayment(Payment payment, GatewayPaymentSnapshot snapshot, String expectedStoreId) {
        if (!payment.isProvider(PaymentProviderType.PORTONE)) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 건이 아닙니다.");
        }
        if (payment.getExternalPaymentId() == null || !payment.getExternalPaymentId().equals(snapshot.externalPaymentId())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 paymentId가 일치하지 않습니다.");
        }
        if (expectedStoreId != null && !expectedStoreId.isBlank() && !expectedStoreId.equals(snapshot.storeId())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 storeId가 일치하지 않습니다.");
        }
        if (!"KRW".equals(snapshot.currency())) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "지원하지 않는 포트원 결제 통화입니다.");
        }
        if (snapshot.amount() == null) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "포트원 결제 금액이 누락되었습니다.");
        }
        if (payment.getPrice() != snapshot.amount()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH, "결제 금액이 일치하지 않습니다.");
        }
    }

    private String refundCreditReferenceId(Payment payment) {
        return "PAYMENT_REFUND:" + payment.getProviderOrDefault() + ":" + payment.getOrderId();
    }

    public record PaymentConfirmationStart(Payment payment, boolean alreadyCompleted) {
    }

    public record PaymentRefundStart(
            Long paymentId,
            String orderId,
            PaymentProviderType provider,
            String payToken,
            String externalPaymentId,
            int amount,
            int creditAmount,
            boolean alreadyRefunded,
            int creditBalance
    ) {
        static PaymentRefundStart pending(Payment payment, int creditBalance) {
            return from(payment, false, creditBalance);
        }

        static PaymentRefundStart alreadyRefunded(Payment payment, int creditBalance) {
            return from(payment, true, creditBalance);
        }

        private static PaymentRefundStart from(Payment payment, boolean alreadyRefunded, int creditBalance) {
            return new PaymentRefundStart(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getProviderOrDefault(),
                    payment.getPayToken(),
                    payment.getExternalPaymentId(),
                    payment.getPrice(),
                    payment.getCreditAmount(),
                    alreadyRefunded,
                    creditBalance
            );
        }
    }
}
