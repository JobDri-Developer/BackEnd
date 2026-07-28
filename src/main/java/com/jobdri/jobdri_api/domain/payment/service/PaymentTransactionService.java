package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.TossPayCallbackRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentOrderStatusResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;
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

    private static final String TOSS_PAY_COMPLETE = "PAY_COMPLETE";
    private static final String TOSS_PAY_CANCEL = "PAY_CANCEL";

    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final CreditService creditService;

    @Transactional
    public Payment createPendingPayment(Long userId, CreditPlan plan, String orderId) {
        User user = userService.getUser(userId);
        return paymentRepository.save(Payment.createPending(
                user,
                "JobDri 크레딧 " + plan.getName(),
                orderId,
                plan.getCode(),
                plan.getCreditAmount(),
                plan.getPrice()
        ));
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
    public PaymentConfirmResponse handleTossPayCallback(TossPayCallbackRequest request) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderNo())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderNo=" + request.orderNo()
                ));
        validateTossPayCallback(payment, request);

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        if (TOSS_PAY_COMPLETE.equals(request.status())) {
            User user = userService.getUser(payment.getUser().getId());
            payment.completeByTossPay(request.status());
            int creditBalance = creditService.charge(
                    user,
                    payment.getCreditAmount(),
                    payment.getContent(),
                    payment.getOrderId()
            );
            return PaymentConfirmResponse.of(payment, creditBalance);
        }

        if (TOSS_PAY_CANCEL.equals(request.status())) {
            payment.failByTossPay(request.status());
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }

        payment.updateTossStatus(request.status());
        return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
    }

    @Transactional
    public PaymentConfirmResponse applyTossPayStatus(String orderId, String payToken, String payStatus, int amount) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.PAYMENT_NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다. orderId=" + orderId
                ));
        if (!payment.hasPayToken(payToken) || payment.getPrice() != amount) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "결제 상태 조회 응답 검증에 실패했습니다.");
        }
        payment.markStatusChecked(payStatus);
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return PaymentConfirmResponse.of(payment, userService.getUser(payment.getUser().getId()).getCredit());
        }
        return handleTossPayCallback(new TossPayCallbackRequest(
                payStatus,
                payToken,
                orderId,
                null,
                amount,
                0,
                0,
                null,
                null
        ));
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

    private void validateTossPayCallback(Payment payment, TossPayCallbackRequest request) {
        if (request == null
                || !payment.hasPayToken(request.payToken())
                || !payment.getOrderId().equals(request.orderNo())
                || payment.getPrice() != request.amount()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 콜백 검증에 실패했습니다.");
        }
    }

    public record PaymentConfirmationStart(Payment payment, boolean alreadyCompleted) {
    }
}
