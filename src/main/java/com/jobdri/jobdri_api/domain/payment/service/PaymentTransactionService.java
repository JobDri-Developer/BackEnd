package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
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

    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final CreditService creditService;

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
    public void rollbackConfirmationToPending(Long userId, String orderId, String paymentKey) {
        Payment payment = getOwnedPaymentForUpdate(userId, orderId);
        if (!payment.hasPaymentKey(paymentKey)) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.resetToPending();
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

    public record PaymentConfirmationStart(Payment payment, boolean alreadyCompleted) {
    }
}
