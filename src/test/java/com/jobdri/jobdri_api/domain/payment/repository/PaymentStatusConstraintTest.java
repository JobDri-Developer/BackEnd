package com.jobdri.jobdri_api.domain.payment.repository;

import com.jobdri.jobdri_api.domain.payment.entity.Payment;
import com.jobdri.jobdri_api.domain.payment.entity.PaymentStatus;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentStatusConstraintTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpPaymentStatusCheck() {
        jdbcTemplate.execute("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check");
        jdbcTemplate.execute("""
                ALTER TABLE payments
                ADD CONSTRAINT payments_status_check
                CHECK (status IN ('PENDING', 'PROCESSING', 'UNKNOWN', 'FAILED', 'COMPLETED'))
                """);
    }

    @Test
    @DisplayName("PaymentStatus enum 전체 값은 payments_status_check 제약조건에서 허용된다")
    void paymentStatusCheckAllowsAllPaymentStatusValues() {
        User user = userRepository.save(User.signup("테스트 사용자", "payment-status-check@example.com", "encoded-password"));

        assertThat(Arrays.stream(PaymentStatus.values()).map(Enum::name))
                .containsExactly("PENDING", "PROCESSING", "UNKNOWN", "FAILED", "COMPLETED");

        for (PaymentStatus status : PaymentStatus.values()) {
            Payment payment = Payment.createPending(
                    user,
                    "JobDri 크레딧 1회권",
                    "order-" + status.name().toLowerCase(),
                    "ONE_TIME",
                    1,
                    2500
            );
            applyStatus(payment, status);

            assertThatCode(() -> paymentRepository.saveAndFlush(payment))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("PaymentStatus enum에 없는 값은 payments_status_check 제약조건에서 거절된다")
    void paymentStatusCheckRejectsUnknownDatabaseValue() {
        User user = userRepository.save(User.signup("테스트 사용자", "payment-status-invalid@example.com", "encoded-password"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO payments (
                            user_id,
                            content,
                            order_id,
                            plan_code,
                            credit_amount,
                            price,
                            status,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                user.getId(),
                "JobDri 크레딧 1회권",
                "order-invalid-status",
                "ONE_TIME",
                1,
                2500,
                "CANCELED",
                LocalDateTime.now(),
                LocalDateTime.now()
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void applyStatus(Payment payment, PaymentStatus status) {
        switch (status) {
            case PENDING -> {
            }
            case PROCESSING -> payment.markProcessing("payment-key-processing");
            case UNKNOWN -> {
                payment.markProcessing("payment-key-unknown");
                payment.markUnknown();
            }
            case FAILED -> payment.fail();
            case COMPLETED -> payment.complete("payment-key-completed");
        }
    }
}
