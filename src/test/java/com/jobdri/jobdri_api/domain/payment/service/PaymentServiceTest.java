package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.TossPayCallbackRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentPrepareResponse;
import com.jobdri.jobdri_api.domain.payment.dto.tosspay.TossPayCreateResponse;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossEasyPayInfo;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @MockBean
    private TossPaymentClient tossPaymentClient;

    @MockBean
    private TossPayClient tossPayClient;

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

        assertThat(response.orderId()).startsWith("jobdri-");
        assertThat(response.orderName()).isEqualTo("JobDri 크레딧 5회권");
        assertThat(response.amount()).isEqualTo(11500);
        assertThat(response.creditAmount()).isEqualTo(5);
        assertThat(response.checkoutPage()).startsWith("https://pay.toss.im/checkout/");
        assertThat(response.payToken()).startsWith("pay-token-");
        Payment payment = paymentRepository.findByOrderId(response.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPayToken()).isEqualTo(response.payToken());
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
    @DisplayName("토스페이 PAY_COMPLETE 콜백 수신 시 크레딧을 지급한다")
    void tossPayCallbackCompletesPaymentAndChargesCredit() {
        User user = saveUser("payment-callback-complete@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));

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

        assertThatThrownBy(() -> paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_COMPLETE",
                prepared.payToken(),
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
                .isEqualTo(GeneralErrorCode.PAYMENT_CONFIRM_FAILED);

        assertPaymentPendingWithoutCreditCharge(user, prepared.orderId());
    }

    @Test
    @DisplayName("토스페이 PAY_CANCEL 콜백은 결제를 실패 처리하고 크레딧을 지급하지 않는다")
    void tossPayCallbackDoesNotChargeWhenCanceled() {
        User user = saveUser("payment-callback-cancel@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));

        paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_CANCEL",
                prepared.payToken(),
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
    @DisplayName("토스페이 중간 상태 콜백은 상태만 저장하고 크레딧을 지급하지 않는다")
    void tossPayCallbackDoesNotChargeWhenStatusIsNotComplete() {
        User user = saveUser("payment-callback-progress@example.com");
        mockTossPayCreateSuccess();
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));

        paymentService.handleTossPayCallback(new TossPayCallbackRequest(
                "PAY_APPROVED",
                prepared.payToken(),
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

    private TossPayCallbackRequest tossPayCompleteCallback(PaymentPrepareResponse prepared) {
        return new TossPayCallbackRequest(
                "PAY_COMPLETE",
                prepared.payToken(),
                prepared.orderId(),
                "CARD",
                prepared.amount(),
                0,
                prepared.amount(),
                "2026-07-28 10:00:00",
                "transaction-id"
        );
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
        var ready = new CountDownLatch(threadCount);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Result>> tasks = java.util.stream.IntStream.range(0, threadCount)
                    .mapToObj(i -> (Callable<Result>) () -> {
                        ready.countDown();
                        start.await();
                        return task.call();
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
