package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.CouponRedeemRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.CouponRedeemResponse;
import com.jobdri.jobdri_api.domain.payment.type.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.repository.CouponRedemptionRepository;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class CouponServiceTest {

    private static final long CONCURRENCY_TIMEOUT_SECONDS = 5L;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;

    @Autowired
    private Validator validator;

    @Test
    @DisplayName("유효한 쿠폰 번호를 등록하면 크레딧 1회가 충전되고 사용 이력이 저장된다")
    void redeem() {
        User user = saveUser("coupon-redeem@example.com");

        CouponRedeemResponse response = couponService.redeem(user, new CouponRedeemRequest("  testcoup2026  "));

        assertThat(response.couponCode()).isEqualTo("TESTCOUP2026");
        assertThat(response.creditAmount()).isEqualTo(1);
        assertThat(response.creditBalance()).isEqualTo(2);
        assertThat(response.redeemedAt()).isNotNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(couponRedemptionRepository.findByUserIdAndCouponCode(user.getId(), "TESTCOUP2026")).isPresent();
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.COUPON
        )).hasSize(1);
    }

    @Test
    @DisplayName("설정된 쿠폰 번호와 다르면 예외를 던진다")
    void redeemThrowsWhenCouponCodeIsInvalid() {
        User user = saveUser("coupon-invalid@example.com");

        assertThatThrownBy(() -> couponService.redeem(user, new CouponRedeemRequest("ABCDEFGHIJKM")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.COUPON_INVALID);
    }

    @Test
    @DisplayName("같은 사용자가 동일 쿠폰을 다시 등록하면 중복 사용을 막는다")
    void redeemThrowsWhenCouponAlreadyRedeemed() {
        User user = saveUser("coupon-duplicate@example.com");
        couponService.redeem(user, new CouponRedeemRequest("TESTCOUP2026"));

        assertThatThrownBy(() -> couponService.redeem(user, new CouponRedeemRequest("TESTCOUP2026")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.COUPON_ALREADY_REDEEMED);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.COUPON
        )).hasSize(1);
    }

    @Test
    @DisplayName("동일 쿠폰 등록 요청이 동시에 들어와도 한 번만 충전한다")
    void redeemConcurrentlyChargesOnlyOnce() throws Exception {
        User user = saveUser("coupon-concurrent@example.com");
        CouponRedeemRequest request = new CouponRedeemRequest("TESTCOUP2026");

        List<Result> results = runConcurrently(2, () -> {
            try {
                couponService.redeem(user, request);
                return Result.ok();
            } catch (Exception e) {
                return Result.failure(e);
            }
        });

        assertThat(results).filteredOn(Result::success).hasSize(1);
        assertThat(results)
                .filteredOn(result -> !result.success())
                .allSatisfy(result -> assertThat(result.exception())
                        .isInstanceOf(GeneralException.class)
                        .extracting("code")
                        .isEqualTo(GeneralErrorCode.COUPON_ALREADY_REDEEMED));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(2);
        assertThat(couponRedemptionRepository.findByUserIdAndCouponCode(user.getId(), "TESTCOUP2026")).isPresent();
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.COUPON
        )).hasSize(1);
    }

    @Test
    @DisplayName("쿠폰 번호는 대시 없는 영문 또는 숫자 12자리 형식만 허용한다")
    void validateCouponCodeFormat() {
        assertThat(validator.validate(new CouponRedeemRequest("ABCDEFGHIJKL"))).isEmpty();
        assertThat(validator.validate(new CouponRedeemRequest("ABCD-EFGH-IJKL"))).isNotEmpty();
        assertThat(validator.validate(new CouponRedeemRequest("ABCDEFGHIJK"))).isNotEmpty();
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
            if (!ready.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                fail("Concurrent test setup timed out while waiting for worker threads to be ready.");
            }
            start.countDown();

            List<Result> results = new java.util.ArrayList<>();
            for (var future : futures) {
                try {
                    results.add(future.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (TimeoutException e) {
                    fail("Concurrent test timed out while waiting for worker result.", e);
                }
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
