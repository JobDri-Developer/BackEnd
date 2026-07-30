package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePaymentResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentOrderStatusResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;
import com.jobdri.jobdri_api.domain.payment.entity.PortOnePaymentStatus;
import com.jobdri.jobdri_api.domain.payment.entity.TossPayStatus;
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

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
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
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
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
    public PaymentConfirmResponse applyTossPayStatus(String orderId, String payToken, TossPayStatus tossPayStatus, int amount) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderId=" + orderId
                ));
        validateTossPayStatus(payment, payToken, amount);

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            if (tossPayStatus == TossPayStatus.PAY_CANCEL) {
                payment.updateTossStatus(tossPayStatus.name());
            }
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.PROCESSING) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED, "처리할 수 없는 결제 상태입니다.");
        }

        if (tossPayStatus == TossPayStatus.PAY_COMPLETE) {
            User user = userService.getUser(payment.getUser().getId());
            payment.completeByTossPay(tossPayStatus.name());
            int creditBalance = creditService.charge(
                    user,
                    payment.getCreditAmount(),
                    payment.getContent(),
                    payment.getOrderId()
            );
            return PaymentConfirmResponse.of(payment, creditBalance);
        }

        if (tossPayStatus == TossPayStatus.PAY_CANCEL) {
            payment.failByTossPay(tossPayStatus.name());
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        payment.updateTossStatus(tossPayStatus.name());
        return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
    }

    @Transactional
    public PaymentConfirmResponse applyPortOnePayment(Long userId, PortOnePaymentResponse response, String expectedStoreId) {
        return applyPortOnePayment(userId, response, expectedStoreId, true);
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
        Payment payment = paymentRepository.findByOrderIdForUpdate(response.id())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. paymentId=" + response.id()
                ));
        if (requireOwner && !payment.belongsTo(userId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 결제에 접근할 수 없습니다.");
        }
        validatePortOnePayment(payment, response, expectedStoreId);

        PortOnePaymentStatus portOneStatus = PortOnePaymentStatus.from(response.status());
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            payment.updatePortOneStatus(response.status(), response.transactionId());
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        if (portOneStatus == PortOnePaymentStatus.PAID) {
            User user = userService.getUser(payment.getUser().getId());
            payment.completeByPortOne(response.status(), response.transactionId());
            int creditBalance = creditService.charge(
                    user,
                    payment.getCreditAmount(),
                    payment.getContent(),
                    "PAYMENT:PORTONE:" + payment.getOrderId()
            );
            return PaymentConfirmResponse.of(payment, creditBalance);
        }

        if (portOneStatus == PortOnePaymentStatus.FAILED || portOneStatus == PortOnePaymentStatus.CANCELLED) {
            payment.failByPortOne(response.status(), response.transactionId());
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        if (portOneStatus == PortOnePaymentStatus.UNKNOWN || portOneStatus == PortOnePaymentStatus.PARTIAL_CANCELLED) {
            payment.markPortOneUnknown(response.status(), response.transactionId());
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        payment.updatePortOneStatus(response.status(), response.transactionId());
        return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
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

    public record PaymentConfirmationStart(Payment payment, boolean alreadyCompleted) {
    }
}
