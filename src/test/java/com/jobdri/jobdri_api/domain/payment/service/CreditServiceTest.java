package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CreditServiceTest {

    @Autowired
    private CreditService creditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Test
    @DisplayName("같은 referenceId로 크레딧 충전을 재시도해도 한 번만 반영한다")
    void chargeIsIdempotentByReferenceId() {
        User user = saveUser("credit-charge-idempotent@example.com");

        int first = creditService.charge(user, 5, "테스트 충전", "payment-order-1");
        int second = creditService.charge(user, 5, "테스트 충전", "payment-order-1");

        assertThat(first).isEqualTo(6);
        assertThat(second).isEqualTo(6);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(6);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.CHARGE
        )).hasSize(1);
    }

    @Test
    @DisplayName("같은 referenceId로 크레딧 사용을 재시도해도 한 번만 차감한다")
    void useIsIdempotentByReferenceId() {
        User user = saveUser("credit-use-idempotent@example.com");

        int first = creditService.use(user, 1, "테스트 사용", "mockApplyId=101");
        int second = creditService.use(user, 1, "테스트 사용", "mockApplyId=101");

        assertThat(first).isZero();
        assertThat(second).isZero();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isZero();
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).hasSize(1);
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }
}
