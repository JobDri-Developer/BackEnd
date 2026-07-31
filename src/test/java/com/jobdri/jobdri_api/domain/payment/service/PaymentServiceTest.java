package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOneCancelResponse;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOneCancellation;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOneAmount;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePaymentResponse;
import com.jobdri.jobdri_api.domain.payment.dto.portone.PortOnePrepareData;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentRefundRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PortOnePaymentCompleteRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.TossPayCallbackRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentPrepareResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentRefundResponse;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayCreateResponse;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayRefundResponse;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayStatusResponse;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossEasyPayInfo;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.controller.PaymentController;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;
import com.jobdri.jobdri_api.domain.payment.entity.TossPayStatus;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.payment.repository.PaymentRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceTest {

    private static final String TEST_PORTONE_WEBHOOK_SECRET = "whsec_dGVzdC13ZWJob29rLXNlY3JldA==";
    private static final String ORDER_ID_PATTERN = "^jobdri-[0-9a-f]{32}$";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private TossPayClient tossPayClient;

    @MockitoBean
    private PortOneClient portOneClient;

    @Test
    @DisplayName("크레딧 플랜 목록을 조회한다")
    void getPlans() {
        var plans = paymentService.getPlans();

        assertThat(plans).hasSize(3);
        assertThat(plans)
                .extracting("planCode", "creditAmount", "price")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("ONE_TIME", 1, 2500),
                        org.assertj.core.groups.Tuple.tuple("FIVE_TIMES", 5, 11500),
                        org.assertj.core.groups.Tuple.tuple("TEN_TIMES", 10, 19900)
                );
    }

    @Test
    @DisplayName("결제 준비 시 PENDING 결제 정보를 생성한다")
    void prepare() {
        User user = saveUser("payment-prepare@example.com");
        mockTossPayCreateSuccess();

        PaymentPrepareResponse response = paymentService.prepare(user, new PaymentPrepareRequest("FIVE_TIMES"));

        assertThat(response.orderId()).matches(ORDER_ID_PATTERN);
        assertThat(response.orderName()).isEqualTo("JobDri 크레딧 5회권");
        assertThat(response.amount()).isEqualTo(11500);
        assertThat(response.creditAmount()).isEqualTo(5);
        assertThat(response.provider()).isEqualTo(PaymentProviderType.TOSS_PAY_DIRECT);
        assertThat(response.checkoutPage()).startsWith("https://pay.toss.im/checkout/");
        Payment payment = paymentRepository.findByOrderId(response.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProviderOrDefault()).isEqualTo(PaymentProviderType.TOSS_PAY_DIRECT);
        assertThat(payment.getPayToken()).startsWith("pay-token-");
        assertThat(payment.getCheckoutPage()).isEqualTo(response.checkoutPage());
    }

    @Test
    @DisplayName("토스 결제 승인 성공 시 크레딧을 충전하고 거래 내역을 저장한다")
    void confirm() {
        User user = saveUser("payment-confirm@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(tossPayResponse(paymentKey, prepared, 2500));

        PaymentConfirmResponse response = paymentService.confirm(
                user,
                new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500)
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.creditBalance()).isEqualTo(2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("이미 완료된 동일 결제 승인 재시도는 기존 결과를 반환하고 중복 충전하지 않는다")
    void confirmReturnsExistingResultWhenAlreadyCompleted() {
        User user = saveUser("payment-confirm-idempotent@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(tossPayResponse(paymentKey, prepared, 2500));
        PaymentConfirmRequest request = new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500);

        PaymentConfirmResponse first = paymentService.confirm(user, request);
        PaymentConfirmResponse second = paymentService.confirm(user, request);

        assertThat(first.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("결제 승인 요청 금액이 준비 금액과 다르면 예외를 던진다")
    void confirmThrowsWhenAmountMismatch() {
        User user = saveUser("payment-amount-mismatch@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest(CreditPlan.ONE_TIME.getCode()));

        assertThatThrownBy(() -> paymentService.confirm(
                user,
                new PaymentConfirmRequest("payment-key", prepared.orderId(), 1000)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("토스 결제 승인 실패 시 결제 상태를 FAILED로 변경한다")
    void confirmMarksPaymentAsFailedWhenTossConfirmFails() {
        User user = saveUser("payment-confirm-fail@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenThrow(new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스 승인 실패"));

        assertThatThrownBy(() -> paymentService.confirm(
                user,
                new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).isEmpty();
    }

    @Test
    @DisplayName("토스 승인 타임아웃이면 결제를 UNKNOWN으로 남기고 재시도를 허용한다")
    void confirmAllowsRetryWhenTossConfirmTimesOut() {
        User user = saveUser("payment-confirm-timeout@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        PaymentConfirmRequest request = new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500);

        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenThrow(new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "timeout"))
                .thenReturn(tossPayResponse(paymentKey, prepared, 2500));

        assertThatThrownBy(() -> paymentService.confirm(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);

        Payment timedOutPayment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(timedOutPayment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);

        PaymentConfirmResponse retryResponse = paymentService.confirm(user, request);

        assertThat(retryResponse.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("동일 결제 승인 요청이 동시에 들어와도 한 번만 크레딧을 충전한다")
    void confirmConcurrentlyChargesOnlyOnce() throws Exception {
        User user = saveUser("payment-concurrent-confirm@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(tossPayResponse(paymentKey, prepared, 2500));
        PaymentConfirmRequest request = new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500);

        List<Result> results = runConcurrently(2, () -> {
            try {
                paymentService.confirm(user, request);
                return Result.ok();
            } catch (Exception e) {
                return Result.failure(e);
            }
        });

        assertThat(results).filteredOn(Result::success).isNotEmpty();
        assertThat(results)
                .filteredOn(result -> !result.success())
                .allSatisfy(result -> assertThat(result.exception())
                        .isInstanceOf(GeneralException.class)
                        .extracting("code")
                        .isEqualTo(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("토스 승인 응답이 간편결제가 아니면 결제를 실패 처리하고 크레딧을 충전하지 않는다")
    void confirmThrowsWhenPaymentMethodIsNotEasyPay() {
        User user = saveUser("payment-method-card@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "카드",
                        null
                ));

        assertThatThrownBy(() -> paymentService.confirm(
                user,
                new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        assertPaymentFailedWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("토스 승인 응답의 easyPay가 없으면 결제를 실패 처리하고 크레딧을 충전하지 않는다")
    void confirmThrowsWhenEasyPayIsMissing() {
        User user = saveUser("payment-method-easypay-missing@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "간편결제",
                        null
                ));

        assertThatThrownBy(() -> paymentService.confirm(
                user,
                new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        assertPaymentFailedWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("토스 승인 응답의 간편결제 제공사가 토스페이가 아니면 결제를 실패 처리하고 크레딧을 충전하지 않는다")
    void confirmThrowsWhenEasyPayProviderIsNotTossPay() {
        User user = saveUser("payment-method-other-easypay@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "간편결제",
                        new TossEasyPayInfo("카카오페이")
                ));

        assertThatThrownBy(() -> paymentService.confirm(
                user,
                new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        assertPaymentFailedWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("토스페이 결제 생성 실패 시 결제 상태를 FAILED로 변경하고 크레딧을 지급하지 않는다")
    void prepareMarksPaymentAsFailedWhenTossPayCreateFails() {
        User user = saveUser("payment-create-failed@example.com");
        when(tossPayClient.createPayment(anyString(), anyInt(), anyString()))
                .thenThrow(new GeneralException(GeneralErrorCode.PAYMENT_CONFIRM_FAILED, "토스페이 생성 실패"));

        assertThatThrownBy(() -> paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        Payment payment = paymentRepository.findAllByUserId(user.getId()).getFirst();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
    }

    @Test
    @DisplayName("토스페이 결제 생성 타임아웃 시 결제 상태를 UNKNOWN으로 남긴다")
    void prepareMarksPaymentAsUnknownWhenTossPayCreateTimesOut() {
        User user = saveUser("payment-create-timeout@example.com");
        when(tossPayClient.createPayment(anyString(), anyInt(), anyString()))
                .thenThrow(new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "timeout"));

        assertThatThrownBy(() -> paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);

        Payment payment = paymentRepository.findAllByUserId(user.getId()).getFirst();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.getPayToken()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
    }

    @Test
    @DisplayName("토스페이 PAY_COMPLETE 콜백 수신 시 크레딧을 지급한다")
    void tossPayCallbackCompletesPaymentAndChargesCredit() {
        User user = saveUser("payment-callback-complete@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, prepared.amount());

        paymentService.handleTossPayCallback(tossPayCompleteCallback(prepared));

        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTossStatus()).isEqualTo("PAY_COMPLETE");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("동일 토스페이 콜백을 재수신해도 크레딧은 한 번만 지급된다")
    void tossPayCallbackIsIdempotent() {
        User user = saveUser("payment-callback-idempotent@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, prepared.amount());
        TossPayCallbackRequest callback = tossPayCompleteCallback(prepared);

        paymentService.handleTossPayCallback(callback);
        paymentService.handleTossPayCallback(callback);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("토스페이 PAY_COMPLETE 콜백이 동시에 들어와도 크레딧은 한 번만 지급된다")
    void tossPayCallbackConcurrentlyChargesOnlyOnce() throws Exception {
        User user = saveUser("payment-callback-concurrent@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, prepared.amount());
        TossPayCallbackRequest callback = tossPayCompleteCallback(prepared);

        List<Result> results = runConcurrently(2, () -> {
            try {
                paymentService.handleTossPayCallback(callback);
                return Result.ok();
            } catch (Exception e) {
                return Result.failure(e);
            }
        });

        assertThat(results).filteredOn(Result::success).hasSize(2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("토스페이 콜백 payToken이 일치하지 않으면 크레딧을 지급하지 않는다")
    void tossPayCallbackThrowsWhenPayTokenMismatches() {
        User user = saveUser("payment-callback-token-mismatch@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        when(tossPayClient.getPaymentStatus("wrong-pay-token", prepared.orderId()))
                .thenReturn(tossPayStatus("wrong-pay-token", prepared.orderId(), TossPayStatus.PAY_COMPLETE, prepared.amount()));

        assertThatThrownBy(() -> paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_COMPLETE",
                "wrong-pay-token",
                prepared.orderId(),
                "CARD",
                prepared.amount(),
                0,
                prepared.amount(),
                "2026-07-28 10:00:00",
                "transaction-id"
        )))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        assertPaymentPendingWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("토스페이 콜백 금액이 일치하지 않으면 크레딧을 지급하지 않는다")
    void tossPayCallbackThrowsWhenAmountMismatches() {
        User user = saveUser("payment-callback-amount-mismatch@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, 1000);

        assertThatThrownBy(() -> paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_COMPLETE",
                savedPayToken(prepared),
                prepared.orderId(),
                "CARD",
                1000,
                0,
                1000,
                "2026-07-28 10:00:00",
                "transaction-id"
        )))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertPaymentPendingWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("토스페이 PAY_CANCEL 콜백은 결제를 실패 처리하고 크레딧을 지급하지 않는다")
    void tossPayCallbackDoesNotChargeWhenCanceled() {
        User user = saveUser("payment-callback-cancel@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_CANCEL, prepared.amount());

        paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_CANCEL",
                savedPayToken(prepared),
                prepared.orderId(),
                "CARD",
                prepared.amount(),
                0,
                0,
                null,
                null
        ));

        assertPaymentFailedWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("이미 완료된 결제에 PAY_CANCEL 콜백이 와도 크레딧을 회수하지 않는다")
    void tossPayCallbackIgnoresCancelAfterCompleted() {
        User user = saveUser("payment-callback-cancel-after-complete@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, prepared.amount());
        paymentService.handleTossPayCallback(tossPayCompleteCallback(prepared));

        mockTossPayStatus(prepared, TossPayStatus.PAY_CANCEL, prepared.amount());
        paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_CANCEL",
                savedPayToken(prepared),
                prepared.orderId(),
                "CARD",
                prepared.amount(),
                0,
                0,
                null,
                null
        ));

        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTossStatus()).isEqualTo("PAY_CANCEL");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("토스페이 중간 상태 콜백은 상태만 저장하고 크레딧을 지급하지 않는다")
    void tossPayCallbackDoesNotChargeWhenStatusIsNotComplete() {
        User user = saveUser("payment-callback-progress@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_APPROVED, prepared.amount());

        paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_APPROVED",
                savedPayToken(prepared),
                prepared.orderId(),
                "CARD",
                prepared.amount(),
                0,
                0,
                null,
                null
        ));

        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTossStatus()).isEqualTo("PAY_APPROVED");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).isEmpty();
    }

    @Test
    @DisplayName("복구 불가능한 토스페이 콜백 검증 실패는 컨트롤러에서 200으로 응답한다")
    void tossPayCallbackControllerAcknowledgesUnrecoverableValidationFailure() {
        var response = paymentController.tossPayCallback(new TossPayCallbackRequest(
                "PAY_COMPLETE",
                null,
                null,
                "CARD",
                0,
                0,
                0,
                null,
                null
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("재시도 가능한 토스페이 콜백 상태 조회 실패는 컨트롤러에서 전파한다")
    void tossPayCallbackControllerPropagatesRetryableStatusFailure() {
        User user = saveUser("payment-callback-retryable@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        when(tossPayClient.getPaymentStatus(savedPayToken(prepared), prepared.orderId()))
                .thenThrow(new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "timeout"));

        assertThatThrownBy(() -> paymentController.tossPayCallback(tossPayCompleteCallback(prepared)))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);
    }

    @Test
    @DisplayName("포트원 결제 준비 시 SDK 공개 파라미터만 반환한다")
    void preparePortOneReturnsPublicSdkParameters() {
        User user = saveUser("payment-portone-prepare@example.com");
        mockPortOnePrepareData();

        PaymentPrepareResponse response = paymentService.prepare(
                user,
                new PaymentPrepareRequest("ONE_TIME", "PORTONE")
        );

        assertThat(response.provider()).isEqualTo(PaymentProviderType.PORTONE);
        assertThat(response.orderId()).matches(ORDER_ID_PATTERN);
        assertThat(response.portOneStoreId()).isEqualTo("store-test");
        assertThat(response.portOneChannelKey()).isEqualTo("channel-key-test");
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.redirectUrl()).isEqualTo("http://localhost:3000/credit/payment-result");
        assertThat(response.checkoutPage()).isNull();
        Payment payment = paymentRepository.findByOrderId(response.orderId()).orElseThrow();
        assertThat(payment.getProviderOrDefault()).isEqualTo(PaymentProviderType.PORTONE);
        assertThat(payment.getExternalPaymentId()).isEqualTo(response.orderId());
    }

    @Test
    @DisplayName("포트원 PAID 결제 검증 성공 시 크레딧을 지급한다")
    void completePortOneChargesCreditWhenPaid() {
        User user = saveUser("payment-portone-paid@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());

        PaymentConfirmResponse response = paymentService.completePortOne(
                user,
                new PortOnePaymentCompleteRequest(prepared.orderId())
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(payment.getExternalStatus()).isEqualTo("PAID");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("포트원 결제 금액이 일치하지 않으면 크레딧을 지급하지 않는다")
    void completePortOneRejectsAmountMismatch() {
        User user = saveUser("payment-portone-amount-mismatch@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", 1000);

        assertThatThrownBy(() -> paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId())))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertPaymentPendingWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("포트원 FAILED 상태에서는 크레딧을 지급하지 않는다")
    void completePortOneDoesNotChargeWhenFailed() {
        User user = saveUser("payment-portone-failed@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "FAILED", prepared.amount());

        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));

        assertPaymentFailedWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("포트원 CANCELLED 상태에서는 크레딧을 지급하지 않는다")
    void completePortOneDoesNotChargeWhenCancelled() {
        User user = saveUser("payment-portone-cancelled@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "CANCELLED", prepared.amount());

        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));

        assertPaymentFailedWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("다른 사용자의 포트원 결제 완료 요청은 거부한다")
    void completePortOneRejectsOtherUserPayment() {
        User owner = saveUser("payment-portone-owner@example.com");
        User other = saveUser("payment-portone-other@example.com");
        PaymentPrepareResponse prepared = preparePortOne(owner);
        mockPortOnePayment(prepared, "PAID", prepared.amount());

        assertThatThrownBy(() -> paymentService.completePortOne(other, new PortOnePaymentCompleteRequest(prepared.orderId())))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        assertPaymentPendingWithoutCreditCharge(owner, prepared.orderId());
    }

    @Test
    @DisplayName("포트원 결제 완료 요청을 중복 호출해도 크레딧은 한 번만 지급된다")
    void completePortOneIsIdempotent() {
        User user = saveUser("payment-portone-idempotent@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());

        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("관리자는 포트원 완료 결제를 전액 환불하고 지급 크레딧을 회수한다")
    void refundPortOnePayment() {
        User admin = saveAdmin("payment-portone-refund-admin@example.com");
        User user = saveUser("payment-portone-refund-user@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        mockPortOneCancel(payment, "테스트 환불");

        PaymentRefundResponse response = paymentService.refund(
                admin,
                payment.getId(),
                new PaymentRefundRequest("테스트 환불")
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.creditBalance()).isEqualTo(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getRefundReason()).isEqualTo("테스트 환불");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).anySatisfy(transaction ->
                assertThat(transaction.getReferenceId()).isEqualTo("PAYMENT_REFUND:PORTONE:" + prepared.orderId())
        );
    }

    @Test
    @DisplayName("포트원 환불 중복 요청은 외부 취소와 크레딧 회수를 한 번만 수행한다")
    void refundPortOnePaymentIsIdempotent() {
        User admin = saveAdmin("payment-portone-refund-idempotent-admin@example.com");
        User user = saveUser("payment-portone-refund-idempotent-user@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        mockPortOneCancel(payment, "테스트 환불");

        paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불"));
        paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불"));

        verify(portOneClient, times(1)).cancelPayment(prepared.orderId(), prepared.amount(), "테스트 환불");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).filteredOn(transaction -> transaction.getReferenceId().equals("PAYMENT_REFUND:PORTONE:" + prepared.orderId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("포트원 환불 중복 동시 요청은 외부 취소와 크레딧 회수를 한 번만 수행한다")
    void refundPortOnePaymentConcurrentlyIsIdempotent() throws Exception {
        User admin = saveAdmin("payment-portone-refund-concurrent-admin@example.com");
        User user = saveUser("payment-portone-refund-concurrent-user@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        mockPortOneCancel(payment, "테스트 환불");

        List<Result> results = runConcurrently(2, () -> {
            try {
                paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불"));
                return Result.ok();
            } catch (Exception e) {
                return Result.failure(e);
            }
        });

        assertThat(results).allSatisfy(result -> {
            if (!result.success()) {
                assertThat(result.exception())
                        .isInstanceOf(GeneralException.class)
                        .extracting("code")
                        .isEqualTo(GeneralErrorCode.PAYMENT_ALREADY_PROCESSED);
            }
        });
        verify(portOneClient, times(1)).cancelPayment(prepared.orderId(), prepared.amount(), "테스트 환불");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).filteredOn(transaction -> transaction.getReferenceId().equals("PAYMENT_REFUND:PORTONE:" + prepared.orderId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("포트원 환불 외부 실패는 결제 완료 상태와 크레딧을 유지해 재시도 가능하게 한다")
    void refundPortOnePaymentCanRetryAfterExternalFailure() {
        User admin = saveAdmin("payment-portone-refund-retry-admin@example.com");
        User user = saveUser("payment-portone-refund-retry-user@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        when(portOneClient.cancelPayment(prepared.orderId(), prepared.amount(), "테스트 환불"))
                .thenThrow(new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "portone 5xx"))
                .thenReturn(new PortOneCancelResponse(new PortOneCancellation("cancel-" + prepared.orderId(), "SUCCEEDED", prepared.amount())));

        assertThatThrownBy(() -> paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);

        Payment afterFailure = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(afterFailure.getRefundReason()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);

        paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불"));

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        verify(portOneClient, times(2)).cancelPayment(prepared.orderId(), prepared.amount(), "테스트 환불");
    }

    @Test
    @DisplayName("환불할 크레딧 잔액이 부족하면 외부 환불을 호출하지 않는다")
    void refundRejectsWhenCreditAlreadySpent() {
        User admin = saveAdmin("payment-portone-refund-insufficient-admin@example.com");
        User user = saveUser("payment-portone-refund-insufficient-user@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        User chargedUser = userRepository.findById(user.getId()).orElseThrow();
        chargedUser.decreaseCredit(2);
        userRepository.saveAndFlush(chargedUser);

        assertThatThrownBy(() -> paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INSUFFICIENT_CREDIT);

        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("관리자는 토스페이 직접 연동 완료 결제를 전액 환불하고 지급 크레딧을 회수한다")
    void refundTossPayDirectPayment() {
        User admin = saveAdmin("payment-tosspay-refund-admin@example.com");
        User user = saveUser("payment-tosspay-refund-user@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, prepared.amount());
        paymentService.handleTossPayCallback(tossPayCompleteCallback(prepared));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        String refundNo = "jobdri-refund-" + payment.getId();
        when(tossPayClient.refundPayment(
                savedPayToken(prepared),
                prepared.orderId(),
                refundNo,
                prepared.amount(),
                "관리자 결제 환불"
        )).thenReturn(tossPayRefundResponse(savedPayToken(prepared), refundNo, prepared.amount()));

        PaymentRefundResponse response = paymentService.refund(admin, payment.getId(), new PaymentRefundRequest(null));

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.creditBalance()).isEqualTo(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getTossStatus()).isEqualTo("REFUND_SUCCESS");
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getRefundReason()).isEqualTo("관리자 결제 환불");
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).anySatisfy(transaction ->
                assertThat(transaction.getReferenceId()).isEqualTo("PAYMENT_REFUND:TOSS_PAY_DIRECT:" + prepared.orderId())
        );
    }

    @Test
    @DisplayName("토스페이 환불 외부 실패는 결제 완료 상태와 크레딧을 유지해 재시도 가능하게 한다")
    void refundTossPayDirectPaymentCanRetryAfterExternalFailure() {
        User admin = saveAdmin("payment-tosspay-refund-retry-admin@example.com");
        User user = saveUser("payment-tosspay-refund-retry-user@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        mockTossPayStatus(prepared, TossPayStatus.PAY_COMPLETE, prepared.amount());
        paymentService.handleTossPayCallback(tossPayCompleteCallback(prepared));
        Payment payment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        String refundNo = "jobdri-refund-" + payment.getId();
        when(tossPayClient.refundPayment(
                savedPayToken(prepared),
                prepared.orderId(),
                refundNo,
                prepared.amount(),
                "테스트 환불"
        ))
                .thenThrow(new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "timeout"))
                .thenReturn(tossPayRefundResponse(savedPayToken(prepared), refundNo, prepared.amount()));

        assertThatThrownBy(() -> paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);

        Payment afterFailure = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(afterFailure.getRefundReason()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);

        paymentService.refund(admin, payment.getId(), new PaymentRefundRequest("테스트 환불"));

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        verify(tossPayClient, times(2)).refundPayment(
                savedPayToken(prepared),
                prepared.orderId(),
                refundNo,
                prepared.amount(),
                "테스트 환불"
        );
    }

    @Test
    @DisplayName("포트원 complete와 webhook이 동시에 처리되어도 크레딧은 한 번만 지급된다")
    void portOneCompleteAndWebhookConcurrentlyChargeOnlyOnce() throws Exception {
        User user = saveUser("payment-portone-concurrent@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        String rawBody = portOneWebhookBody(prepared.orderId());
        HttpHeaders headers = signedWebhookHeaders(rawBody);

        List<Result> results = runConcurrentlyIndexed(2, index -> {
            try {
                if (index == 0) {
                    paymentController.portOneWebhook(rawBody, headers);
                } else {
                    paymentService.completePortOne(user, new PortOnePaymentCompleteRequest(prepared.orderId()));
                }
                return Result.ok();
            } catch (Exception e) {
                return Result.failure(e);
            }
        });

        assertThat(results).filteredOn(Result::success).hasSize(2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("포트원 위조 웹훅은 거부한다")
    void portOneWebhookRejectsInvalidSignature() {
        String rawBody = portOneWebhookBody("jobdri-forged");
        HttpHeaders headers = new HttpHeaders();
        headers.add("webhook-id", "webhook-id");
        headers.add("webhook-timestamp", String.valueOf(Instant.now().getEpochSecond()));
        headers.add("webhook-signature", "v1,invalid");

        assertThatThrownBy(() -> paymentController.portOneWebhook(rawBody, headers))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("포트원 웹훅 재수신 시 크레딧은 한 번만 지급된다")
    void portOneWebhookIsIdempotent() {
        User user = saveUser("payment-portone-webhook-idempotent@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount());
        String rawBody = portOneWebhookBody(prepared.orderId());
        HttpHeaders headers = signedWebhookHeaders(rawBody);

        paymentController.portOneWebhook(rawBody, headers);
        paymentController.portOneWebhook(rawBody, headers);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("포트원 웹훅의 존재하지 않는 paymentId는 재시도 없이 무시한다")
    void portOneWebhookIgnoresUnknownPaymentId() {
        String rawBody = portOneWebhookBody("jobdri-unknown");
        HttpHeaders headers = signedWebhookHeaders(rawBody);
        when(portOneClient.getPayment("jobdri-unknown"))
                .thenReturn(portOnePayment("jobdri-unknown", "PAID", 2500));
        when(portOneClient.storeId()).thenReturn("store-test");

        paymentController.portOneWebhook(rawBody, headers);

        assertThat(paymentRepository.findByOrderId("jobdri-unknown")).isEmpty();
    }

    @Test
    @DisplayName("포트원 웹훅 금액 불일치는 영구 실패로 기록하고 재시도 없이 무시한다")
    void portOneWebhookIgnoresAmountMismatch() {
        User user = saveUser("payment-portone-webhook-amount-mismatch@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        mockPortOnePayment(prepared, "PAID", prepared.amount() + 100);
        String rawBody = portOneWebhookBody(prepared.orderId());
        HttpHeaders headers = signedWebhookHeaders(rawBody);

        paymentController.portOneWebhook(rawBody, headers);

        assertPaymentPendingWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("포트원 웹훅 결제 조회 timeout은 전파해 포트원 재시도가 가능하게 한다")
    void portOneWebhookPropagatesTimeout() {
        User user = saveUser("payment-portone-webhook-timeout@example.com");
        PaymentPrepareResponse prepared = preparePortOne(user);
        String rawBody = portOneWebhookBody(prepared.orderId());
        HttpHeaders headers = signedWebhookHeaders(rawBody);
        when(portOneClient.getPayment(prepared.orderId()))
                .thenThrow(new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "포트원 조회 timeout"));

        assertThatThrownBy(() -> paymentController.portOneWebhook(rawBody, headers))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);

        assertPaymentPendingWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("결제 주문 상태 조회는 소유자의 주문만 반환한다")
    void getOrderStatus() {
        User user = saveUser("payment-status-owner@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));

        var response = paymentService.getOrderStatus(user, prepared.orderId());

        assertThat(response.orderId()).isEqualTo(prepared.orderId());
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualTo(2500);
    }

    @Test
    @DisplayName("다른 사용자의 결제 주문 상태는 조회할 수 없다")
    void getOrderStatusThrowsWhenUserIsNotOwner() {
        User owner = saveUser("payment-status-real-owner@example.com");
        User other = saveUser("payment-status-other@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(owner, new PaymentPrepareRequest("ONE_TIME"));

        assertThatThrownBy(() -> paymentService.getOrderStatus(other, prepared.orderId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private User saveAdmin(String email) {
        User admin = saveUser(email);
        admin.promoteToAdmin();
        return userRepository.saveAndFlush(admin);
    }

    private PaymentPrepareResponse preparePortOne(User user) {
        mockPortOnePrepareData();
        return paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME", "PORTONE"));
    }

    private void mockPortOnePrepareData() {
        when(portOneClient.prepareData())
                .thenReturn(new PortOnePrepareData(
                        "store-test",
                        "channel-key-test",
                        "http://localhost:3000/credit/payment-result"
                ));
        when(portOneClient.storeId()).thenReturn("store-test");
    }

    private void mockPortOnePayment(PaymentPrepareResponse prepared, String status, int amount) {
        when(portOneClient.getPayment(prepared.orderId()))
                .thenReturn(portOnePayment(prepared.orderId(), status, amount));
    }

    private PortOnePaymentResponse portOnePayment(String paymentId, String status, int amount) {
        return new PortOnePaymentResponse(
                paymentId,
                "transaction-" + paymentId,
                status,
                "store-test",
                "KRW",
                new PortOneAmount(amount)
        );
    }

    private void mockPortOneCancel(Payment payment, String reason) {
        when(portOneClient.cancelPayment(payment.getExternalPaymentId(), payment.getPrice(), reason))
                .thenReturn(new PortOneCancelResponse(new PortOneCancellation(
                        "cancel-" + payment.getOrderId(),
                        "SUCCEEDED",
                        payment.getPrice()
                )));
    }

    private String portOneWebhookBody(String paymentId) {
        return """
                {"type":"Transaction.Paid","timestamp":"2026-07-30T10:00:00.000Z","data":{"paymentId":"%s","storeId":"store-test","transactionId":"transaction-%s"}}
                """.formatted(paymentId, paymentId).trim();
    }

    private HttpHeaders signedWebhookHeaders(String rawBody) {
        String webhookId = "webhook-id-" + rawBody.hashCode();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("webhook-id", webhookId);
        headers.add("webhook-timestamp", timestamp);
        headers.add("webhook-signature", "v1," + signPortOneWebhook(webhookId, timestamp, rawBody));
        return headers;
    }

    private String signPortOneWebhook(String webhookId, String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(portOneWebhookSecretBytes(), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal((webhookId + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] portOneWebhookSecretBytes() {
        String encodedSecret = TEST_PORTONE_WEBHOOK_SECRET.substring("whsec_".length());
        return Base64.getDecoder().decode(encodedSecret);
    }

    private void mockTossPayCreateSuccess() {
        when(tossPayClient.createPayment(anyString(), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    String orderId = invocation.getArgument(0);
                    return new TossPayCreateResponse(
                            0,
                            null,
                            "성공",
                            200,
                            "pay-token-" + orderId,
                            "https://pay.toss.im/checkout/" + orderId
                    );
                });
    }

    private void mockTossPayStatus(PaymentPrepareResponse prepared, TossPayStatus tossPayStatus, int amount) {
        String payToken = savedPayToken(prepared);
        when(tossPayClient.getPaymentStatus(payToken, prepared.orderId()))
                .thenReturn(tossPayStatus(payToken, prepared.orderId(), tossPayStatus, amount));
    }

    private TossPayStatusResponse tossPayStatus(
            String payToken,
            String orderId,
            TossPayStatus tossPayStatus,
            int amount
    ) {
        return new TossPayStatusResponse(
                0,
                null,
                "성공",
                "TEST",
                payToken,
                orderId,
                tossPayStatus.name(),
                "CARD",
                amount,
                0,
                amount
        );
    }

    private TossPayRefundResponse tossPayRefundResponse(String payToken, String refundNo, int amount) {
        return new TossPayRefundResponse(
                0,
                null,
                "성공",
                refundNo,
                payToken,
                "refund-transaction-id",
                "REFUND_SUCCESS",
                amount,
                amount
        );
    }

    private TossPayCallbackRequest tossPayCompleteCallback(PaymentPrepareResponse prepared) {
        return new TossPayCallbackRequest(
                "PAY_COMPLETE",
                savedPayToken(prepared),
                prepared.orderId(),
                "CARD",
                prepared.amount(),
                0,
                prepared.amount(),
                "2026-07-28 10:00:00",
                "transaction-id"
        );
    }

    private String savedPayToken(PaymentPrepareResponse prepared) {
        return paymentRepository.findByOrderId(prepared.orderId()).orElseThrow().getPayToken();
    }

    private TossPaymentConfirmResponse tossPayResponse(
            String paymentKey,
            PaymentPrepareResponse prepared,
            int amount
    ) {
        return new TossPaymentConfirmResponse(
                paymentKey,
                prepared.orderId(),
                prepared.orderName(),
                "DONE",
                amount,
                "간편결제",
                new TossEasyPayInfo("토스페이")
        );
    }

    private void assertPaymentFailedWithoutCreditCharge(User user, String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).isEmpty();
    }

    private void assertPaymentPendingWithoutCreditCharge(User user, String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).isEmpty();
    }

    private List<Result> runConcurrently(int threadCount, Callable<Result> task) throws Exception {
        return runConcurrentlyIndexed(threadCount, ignored -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<Result> runConcurrentlyIndexed(int threadCount, IntFunction<Result> task) throws Exception {
        var ready = new CountDownLatch(threadCount);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Result>> tasks = java.util.stream.IntStream.range(0, threadCount)
                    .mapToObj(i -> (Callable<Result>) () -> {
                        ready.countDown();
                        start.await();
                        return task.apply(i);
                    })
                    .toList();
            var futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            ready.await();
            start.countDown();

            List<Result> results = new java.util.ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record Result(boolean success, Exception exception) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result failure(Exception exception) {
            return new Result(false, exception);
        }
    }
}
