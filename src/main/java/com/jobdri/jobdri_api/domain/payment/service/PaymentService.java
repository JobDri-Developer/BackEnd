package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.*;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.payment.repository.PaymentRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final TossPaymentClient tossPaymentClient;

    @Value("${payment.toss.client-key:}")
    private String tossClientKey;

    @PostConstruct
    void validateConfig() {
        if (tossClientKey == null || tossClientKey.isBlank()) {
            throw new IllegalStateException("payment.toss.client-key must be configured");
        }
    }

    public List<CreditPlanResponse> getPlans() {
        return Arrays.stream(CreditPlan.values())
                .map(CreditPlanResponse::from)
                .toList();
    }

    @Transactional
    public PaymentPrepareResponse prepare(User user, PaymentPrepareRequest request) {
        User validatedUser = userService.validateUser(user);
        CreditPlan plan = CreditPlan.from(request.planCode());
        String orderId = "jobdri-" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.createPending(
                validatedUser,
                "JobDri 크레딧 " + plan.getName(),
                orderId,
                plan.getCode(),
                plan.getCreditAmount(),
                plan.getPrice()
        ));

        return PaymentPrepareResponse.of(payment, tossClientKey);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentConfirmResponse confirm(User user, PaymentConfirmRequest request) {
        User validatedUser = userService.validateUser(user);
        PaymentTransactionService.PaymentConfirmationStart start =
                paymentTransactionService.startConfirmation(validatedUser.getId(), request);
        if (start.alreadyCompleted()) {
            return PaymentConfirmResponse.of(start.payment(), userService.getUser(validatedUser.getId()).getCredit());
        }

        try {
            TossPaymentConfirmResponse tossResponse =
                    tossPaymentClient.confirm(request.paymentKey(), request.orderId(), request.amount());
            validateTossResponse(request, tossResponse);
        } catch (GeneralException e) {
            if (e.getCode() == GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT) {
                paymentTransactionService.rollbackConfirmationToPending(
                        validatedUser.getId(),
                        request.orderId(),
                        request.paymentKey()
                );
                throw e;
            }
            paymentTransactionService.failConfirmation(validatedUser.getId(), request.orderId(), request.paymentKey());
            throw e;
        } catch (RuntimeException e) {
            paymentTransactionService.failConfirmation(validatedUser.getId(), request.orderId(), request.paymentKey());
            throw e;
        }
        return paymentTransactionService.completeConfirmation(validatedUser.getId(), request);
    }

    public CreditBalanceResponse getBalance(User user) {
        User validatedUser = userService.validateUser(user);
        return new CreditBalanceResponse(validatedUser.getCredit());
    }

    public List<CreditTransactionResponse> getTransactions(User user, CreditTransactionType type) {
        User validatedUser = userService.validateUser(user);
        if (type == null) {
            return creditTransactionRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(validatedUser.getId()).stream()
                    .map(CreditTransactionResponse::from)
                    .toList();
        }

        return creditTransactionRepository
                .findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(validatedUser.getId(), type).stream()
                .map(CreditTransactionResponse::from)
                .toList();
    }

    private void validateTossResponse(PaymentConfirmRequest request, TossPaymentConfirmResponse response) {
        if (response == null
                || !request.orderId().equals(response.orderId())
                || !request.paymentKey().equals(response.paymentKey())
                || response.totalAmount() != request.amount()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "결제 승인 응답 검증에 실패했습니다.");
        }
    }
}
