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
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
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
        try (var ignored = LoggingContext.with(
                "payment.prepare.started",
                null,
                paymentContext(null, null, validatedUser.getId(), request.planCode(), plan.getPrice())
        )) {
            log.info("Starting payment preparation");
        }
        String orderId = "jobdri-" + UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.createPending(
                validatedUser,
                "JobDri 크레딧 " + plan.getName(),
                orderId,
                plan.getCode(),
                plan.getCreditAmount(),
                plan.getPrice()
        ));
        try (var ignored = LoggingContext.with(
                "payment.prepare.completed",
                null,
                paymentContext(payment.getOrderId(), null, validatedUser.getId(), plan.getCode(), plan.getPrice())
        )) {
            log.info("Payment preparation completed");
        }

        return PaymentPrepareResponse.of(payment, tossClientKey);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentConfirmResponse confirm(User user, PaymentConfirmRequest request) {
        User validatedUser = userService.validateUser(user);
        Map<String, String> paymentContext = paymentContext(
                request.orderId(),
                maskPaymentKey(request.paymentKey()),
                validatedUser.getId(),
                null,
                request.amount()
        );
        try (var ignored = LoggingContext.with("payment.confirm.started", null, paymentContext)) {
            log.info("Starting payment confirmation");
        }
        PaymentTransactionService.PaymentConfirmationStart start =
                paymentTransactionService.startConfirmation(validatedUser.getId(), request);
        if (start.alreadyCompleted()) {
            try (var ignored = LoggingContext.with("payment.confirm.completed", null, paymentContext)) {
                log.info("Payment confirmation already completed");
            }
            return PaymentConfirmResponse.of(start.payment(), userService.getUser(validatedUser.getId()).getCredit());
        }

        try {
            TossPaymentConfirmResponse tossResponse =
                    tossPaymentClient.confirm(request.paymentKey(), request.orderId(), request.amount());
            validateTossResponse(request, tossResponse);
        } catch (RuntimeException e) {
            if (e instanceof GeneralException generalException
                    && generalException.getCode() == GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT) {
                paymentTransactionService.markConfirmationUnknown(
                        validatedUser.getId(),
                        request.orderId(),
                        request.paymentKey()
                );
                try (var ignored = LoggingContext.with("payment.confirm.unknown", generalException.getCode(), paymentContext)) {
                    log.warn("Payment confirmation state marked as unknown");
                }
                throw e;
            }
            paymentTransactionService.failConfirmation(validatedUser.getId(), request.orderId(), request.paymentKey());
            if (e instanceof GeneralException generalException) {
                try (var ignored = LoggingContext.with("payment.confirm.failed", generalException.getCode(), paymentContext)) {
                    log.warn("Payment confirmation failed: {}", e.getMessage());
                }
            } else {
                try (var ignored = LoggingContext.with("payment.confirm.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
                    log.warn("Payment confirmation failed: {}", e.getMessage());
                }
            }
            throw e;
        }
        PaymentConfirmResponse response = paymentTransactionService.completeConfirmation(validatedUser.getId(), request);
        try (var ignored = LoggingContext.with("payment.confirm.completed", null, paymentContext)) {
            log.info("Payment confirmation completed");
        }
        return response;
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

    private Map<String, String> paymentContext(
            String orderId,
            String maskedPaymentKey,
            Long userId,
            String planCode,
            Integer amount
    ) {
        Map<String, String> context = new LinkedHashMap<>();
        if (orderId != null) {
            context.put("orderId", orderId);
        }
        if (maskedPaymentKey != null) {
            context.put("paymentKey", maskedPaymentKey);
        }
        if (userId != null) {
            context.put("paymentUserId", String.valueOf(userId));
        }
        if (planCode != null) {
            context.put("planCode", planCode);
        }
        if (amount != null) {
            context.put("amount", String.valueOf(amount));
        }
        return context;
    }

    private String maskPaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            return null;
        }
        if (paymentKey.length() <= 10) {
            return "****";
        }
        return paymentKey.substring(0, 6) + "..." + paymentKey.substring(paymentKey.length() - 4);
    }
}
