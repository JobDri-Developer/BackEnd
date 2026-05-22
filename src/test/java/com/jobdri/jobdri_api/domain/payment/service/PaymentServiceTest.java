package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.dto.response.PaymentPrepareResponse;
import com.jobdri.jobdri_api.domain.payment.dto.toss.TossPaymentConfirmResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CreditPlan;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(paymentRepository.findByOrderId(response.orderId())).isPresent();
    }

    @Test
    @DisplayName("토스 결제 승인 성공 시 크레딧을 충전하고 거래 내역을 저장한다")
    void confirm() {
        User user = saveUser("payment-confirm@example.com");
        PaymentPrepareResponse prepared = paymentService.prepare(user, new PaymentPrepareRequest("ONE_TIME"));
        when(tossPaymentClient.confirm("payment-key", prepared.orderId(), 2500))
                .thenReturn(new TossPaymentConfirmResponse(
                        "payment-key",
                        prepared.orderId(),
                        prepared.orderName(),
                        "DONE",
                        2500,
                        "CARD"
                ));

        PaymentConfirmResponse response = paymentService.confirm(
                user,
                new PaymentConfirmRequest("payment-key", prepared.orderId(), 2500)
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

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }
}
