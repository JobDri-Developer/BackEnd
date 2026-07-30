package com.jobdri.jobdri_api.domain.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePaymentResponse;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePrepareData;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOneWebhookPayload;
import com.jobdri.jobdri_api.domain.payment.dto.request.PortOnePaymentCompleteRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.TossPayCallbackRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.*;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayCreateResponse;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayStatusResponse;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.entity.TossPayStatus;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.payment.repository.PaymentRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.BaseErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final String EXPECTED_PAYMENT_METHOD = "간편결제";
    private static final String EXPECTED_EASY_PAY_PROVIDER = "토스페이";

    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final TossPaymentClient tossPaymentClient;
    private final TossPayClient tossPayClient;
    private final PortOneClient portOneClient;
    private final PortOneWebhookVerifier portOneWebhookVerifier;
    private final ObjectMapper objectMapper;

    public List<CreditPlanResponse> getPlans() {
        return Arrays.stream(CreditPlan.values())
                .map(CreditPlanResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentPrepareResponse prepare(User user, PaymentPrepareRequest request) {
        User validatedUser = userService.validateUser(user);
        CreditPlan plan = CreditPlan.from(request.planCode());
        try (var ignored = LoggingContext.with(
                "payment.prepare.started",
                null,
                PaymentLogMasking.paymentContext(null, null, validatedUser.getId(), request.planCode(), plan.getPrice())
        )) {
            log.info("Starting payment preparation");
        }
        PaymentProviderType provider = PaymentProviderType.require(request.provider());
        if (provider == PaymentProviderType.PORTONE) {
            return preparePortOne(validatedUser, plan);
        }
        String orderId = "jobdri-" + UUID.randomUUID();
        Payment payment = paymentTransactionService.createPendingPayment(
                validatedUser.getId(),
                plan,
                orderId,
                PaymentProviderType.TOSS_PAY_DIRECT
        );
        TossPayCreateResponse tossPayResponse;
        try {
            tossPayResponse = tossPayClient.createPayment(
                    payment.getOrderId(),
                    payment.getPrice(),
                    payment.getContent()
            );
        } catch (RuntimeException e) {
            if (e instanceof GeneralException generalException
                    && generalException.getCode() == GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT) {
                paymentTransactionService.markTossPayCreationUnknown(validatedUser.getId(), payment.getOrderId());
                throw e;
            }
            paymentTransactionService.failTossPayCreation(validatedUser.getId(), payment.getOrderId());
            throw e;
        }

        try {
            payment = paymentTransactionService.completeTossPayCreation(
                    validatedUser.getId(),
                    payment.getOrderId(),
                    tossPayResponse.payToken(),
                    tossPayResponse.checkoutPage()
            );
        } catch (RuntimeException e) {
            paymentTransactionService.markTossPayCreationUnknown(
                    validatedUser.getId(),
                    payment.getOrderId(),
                    tossPayResponse.payToken(),
                    tossPayResponse.checkoutPage()
            );
            throw e;
        }
        try (var ignored = LoggingContext.with(
                "payment.create.completed",
                null,
                PaymentLogMasking.paymentContext(payment.getOrderId(), null, validatedUser.getId(), plan.getCode(), plan.getPrice())
        )) {
            log.info("Payment preparation completed");
        }

        return PaymentPrepareResponse.of(payment, validatedUser.getEmail());
    }

    private PaymentPrepareResponse preparePortOne(User validatedUser, CreditPlan plan) {
        PortOnePrepareData prepareData = portOneClient.prepareData();
        String orderId = "jobdri-" + UUID.randomUUID();
        Payment payment = paymentTransactionService.createPortOnePendingPayment(
                validatedUser.getId(),
                plan,
                orderId
        );
        try (var ignored = LoggingContext.with(
                "payment.portone.prepare.completed",
                null,
                PaymentLogMasking.paymentContext(payment.getOrderId(), null, validatedUser.getId(), plan.getCode(), plan.getPrice())
        )) {
            log.info("PortOne payment preparation completed");
        }
        return PaymentPrepareResponse.portOne(
                payment,
                validatedUser.getEmail(),
                prepareData.storeId(),
                prepareData.channelKey(),
                prepareData.redirectUrl()
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentConfirmResponse confirm(User user, PaymentConfirmRequest request) {
        User validatedUser = userService.validateUser(user);
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(
                request.orderId(),
                request.paymentKey(),
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
            validateTossResponse(validatedUser.getId(), request, tossResponse);
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
            BaseErrorCode errorCode = e instanceof GeneralException generalException
                    ? generalException.getCode()
                    : GeneralErrorCode.PAYMENT_CONFIRM_FAILED;
            try (var ignored = LoggingContext.with("payment.confirm.failed", errorCode, paymentContext)) {
                log.warn("Payment confirmation failed: {}", e.getMessage());
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

    public PaymentOrderStatusResponse getOrderStatus(User user, String orderId) {
        User validatedUser = userService.validateUser(user);
        return paymentTransactionService.getOwnedOrderStatus(validatedUser.getId(), orderId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentConfirmResponse completePortOne(User user, PortOnePaymentCompleteRequest request) {
        User validatedUser = userService.validateUser(user);
        PortOnePaymentResponse response = portOneClient.getPayment(request.paymentId());
        return paymentTransactionService.applyPortOnePayment(
                validatedUser.getId(),
                response,
                portOneClient.storeId()
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handlePortOneWebhook(String rawBody, HttpHeaders headers) {
        portOneWebhookVerifier.verify(rawBody, headers);
        PortOneWebhookPayload payload = parsePortOneWebhook(rawBody);
        if (payload.data() == null || payload.data().paymentId() == null || payload.data().paymentId().isBlank()) {
            return;
        }
        try {
            PortOnePaymentResponse response = portOneClient.getPayment(payload.data().paymentId());
            paymentTransactionService.applyPortOneWebhookPayment(response, portOneClient.storeId());
        } catch (GeneralException e) {
            if (e.getCode() == GeneralErrorCode.PAYMENT_NOT_FOUND
                    || e.getCode() == GeneralErrorCode.PAYMENT_CONFIRM_FAILED
                    || e.getCode() == GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH
                    || e.getCode() == GeneralErrorCode.INVALID_PARAMETER) {
                try (var ignored = LoggingContext.with(
                        "payment.portone.webhook.ignored",
                        e.getCode(),
                        PaymentLogMasking.paymentContext(payload.data().paymentId(), null, null)
                )) {
                    log.warn("PortOne webhook ignored: {}", e.getMessage());
                }
                return;
            }
            throw e;
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleTossPayCallback(TossPayCallbackRequest request) {
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(
                request == null ? null : request.orderNo(),
                request == null ? null : request.payToken(),
                request == null ? null : request.amount()
        );
        try (var ignored = LoggingContext.with("payment.callback.received", null, paymentContext)) {
            log.info("Toss Pay callback received");
        }
        try {
            TossPayStatusResponse statusResponse = getVerifiedTossPayStatus(request);
            paymentTransactionService.applyTossPayStatus(
                    statusResponse.orderNo(),
                    statusResponse.payToken(),
                    TossPayStatus.valueOf(statusResponse.payStatus()),
                    statusResponse.amount()
            );
            try (var ignored = LoggingContext.with("payment.callback.completed", null, paymentContext)) {
                log.info("Toss Pay callback processed");
            }
        } catch (GeneralException e) {
            try (var ignored = LoggingContext.with("payment.callback.invalid", e.getCode(), paymentContext)) {
                log.warn("Toss Pay callback validation failed: {}", e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            try (var ignored = LoggingContext.with("payment.callback.failed", GeneralErrorCode.PAYMENT_CONFIRM_FAILED, paymentContext)) {
                log.warn("Toss Pay callback processing failed: {}", e.getMessage());
            }
            throw e;
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentConfirmResponse reconcileTossPayStatus(String payToken, String orderId) {
        TossPayStatusResponse statusResponse = tossPayClient.getPaymentStatus(payToken, orderId);
        validateTossPayStatusResponse(statusResponse, payToken, orderId);
        if (TossPayStatus.valueOf(statusResponse.payStatus()) != TossPayStatus.PAY_COMPLETE) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 결제가 완료되지 않았습니다.");
        }
        return paymentTransactionService.applyTossPayStatus(
                statusResponse.orderNo(),
                statusResponse.payToken(),
                TossPayStatus.valueOf(statusResponse.payStatus()),
                statusResponse.amount()
        );
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

    private void validateTossResponse(Long userId, PaymentConfirmRequest request, TossPaymentConfirmResponse response) {
        if (response == null
                || !request.orderId().equals(response.orderId())
                || !request.paymentKey().equals(response.paymentKey())
                || response.totalAmount() != request.amount()) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "결제 승인 응답 검증에 실패했습니다.");
        }

        String easyPayProvider = response.easyPay() == null ? null : response.easyPay().provider();
        Map<String, String> paymentContext = PaymentLogMasking.paymentContext(
                request.orderId(),
                request.paymentKey(),
                userId,
                null,
                request.amount()
        );
        if (!EXPECTED_PAYMENT_METHOD.equals(response.method())
                || response.easyPay() == null
                || !EXPECTED_EASY_PAY_PROVIDER.equals(easyPayProvider)) {
            try (var ignored = LoggingContext.with(
                    "payment.confirm.unsupported_method",
                    GeneralErrorCode.PAYMENT_CONFIRM_FAILED,
                    paymentContext
            )) {
                log.warn(
                        "Unsupported Toss payment method. method={}, easyPayProvider={}",
                        response.method(),
                        easyPayProvider
                );
            }
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "지원하지 않는 결제수단입니다.");
        }

        try (var ignored = LoggingContext.with("payment.confirm.method_validated", null, paymentContext)) {
            log.info(
                    "Toss payment method validated. method={}, easyPayProvider={}",
                    response.method(),
                    easyPayProvider
            );
        }
    }

    private PortOneWebhookPayload parsePortOneWebhook(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PortOneWebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트원 웹훅 JSON 형식이 잘못되었습니다.", e);
        }
    }

    private TossPayStatusResponse getVerifiedTossPayStatus(TossPayCallbackRequest request) {
        validateCallbackReference(request);
        TossPayStatusResponse statusResponse = tossPayClient.getPaymentStatus(request.payToken(), request.orderNo());
        validateTossPayStatusResponse(statusResponse, request.payToken(), request.orderNo());
        return statusResponse;
    }

    private void validateCallbackReference(TossPayCallbackRequest request) {
        if (request == null || isBlank(request.payToken()) || isBlank(request.orderNo())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "토스페이 콜백 필수값이 누락되었습니다.");
        }
    }

    private void validateTossPayStatusResponse(TossPayStatusResponse response, String payToken, String orderId) {
        if (response == null
                || isBlank(response.payToken())
                || isBlank(response.orderNo())
                || isBlank(response.payStatus())
                || response.amount() == null) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 상태 조회 응답 검증에 실패했습니다.");
        }
        if (!response.payToken().equals(payToken) || !response.orderNo().equals(orderId)) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 상태 조회 응답 식별자가 일치하지 않습니다.");
        }
        try {
            TossPayStatus.valueOf(response.payStatus());
        } catch (IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "지원하지 않는 토스페이 상태입니다.", e);
        }
    }

    public boolean shouldAcknowledgeTossPayCallbackFailure(Exception exception) {
        if (!(exception instanceof GeneralException generalException)) {
            return false;
        }
        BaseErrorCode code = generalException.getCode();
        return code == GeneralErrorCode.INVALID_PARAMETER
                || code == GeneralErrorCode.PAYMENT_CONFIRM_FAILED;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
