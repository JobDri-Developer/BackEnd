package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentPrepareResponse;
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

        PaymentPrepareResponse response = paymentService.prepare(user, new PaymentPrepareRequest("FIVE_TIMES"));

        assertThat(response.orderId()).startsWith("jobdri-");
        assertThat(response.orderName()).isEqualTo("JobDri 크레딧 5회권");
        assertThat(response.amount()).isEqualTo(11500);
        assertThat(response.creditAmount()).isEqualTo(5);
        Payment payment = paymentRepository.findByOrderId(response.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("토스 결제 승인 성공 시 크레딧을 충전하고 거래 내역을 저장한다")
    void confirm() {
        User user = saveUser("payment-confirm@example.com");
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "CARD"
                ));

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
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "CARD"
                ));
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
    @DisplayName("토스 승인 타임아웃이면 결제를 PENDING으로 되돌리고 재시도를 허용한다")
    void confirmAllowsRetryWhenTossConfirmTimesOut() {
        User user = saveUser("payment-confirm-timeout@example.com");
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        PaymentConfirmRequest request = new PaymentConfirmRequest(paymentKey, prepared.orderId(), 2500);

        when(tossPaymentClient.confirm(paymentKey, prepared.orderId(), 2500))
                .thenThrow(new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "timeout"))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "CARD"
                ));

        assertThatThrownBy(() -> paymentService.confirm(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT);

        Payment timedOutPayment = paymentRepository.findByOrderId(prepared.orderId()).orElseThrow();
        assertThat(timedOutPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

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
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        String paymentKey = "payment-key-" + prepared.orderId();
        when(tossPaymentClient.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(new TossPaymentConfirmResponse(
                        paymentKey,
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "CARD"
                ));
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

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
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
