package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.entity.CreditTransaction;
import com.jobdri.jobdri_api.domain.payment.type.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final EntityManager entityManager;

    @Transactional
    public int charge(User user, int amount, String description, String referenceId) {
        return apply(user, CreditTransactionType.CHARGE, amount, description, referenceId);
    }

    @Transactional
    public int use(User user, int amount, String description, String referenceId) {
        return apply(user, CreditTransactionType.USE, amount, description, referenceId);
    }

    @Transactional
    public int refund(User user, int amount, String description, String referenceId) {
        return apply(user, CreditTransactionType.REFUND, amount, description, referenceId);
    }

    @Transactional
    public int coupon(User user, int amount, String description, String referenceId) {
        return apply(user, CreditTransactionType.COUPON, amount, description, referenceId);
    }

    private int apply(User user, CreditTransactionType type, int amount, String description, String referenceId) {
        validatePositiveAmount(amount);
        validateReferenceId(referenceId);
        User managedUser = getManagedUser(user);
        CreditTransaction existingTransaction = creditTransactionRepository
                .findByUserIdAndTypeAndReferenceId(managedUser.getId(), type, referenceId)
                .orElse(null);
        if (existingTransaction != null) {
            return existingTransaction.getBalanceAfter();
        }

        int transactionAmount = resolveTransactionAmount(type, amount);
        applyCreditChange(managedUser, type, amount);
        saveTransaction(managedUser, type, transactionAmount, description, referenceId);
        return managedUser.getCredit();
    }

    private void validatePositiveAmount(int amount) {
        if (amount <= 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "amount는 1 이상이어야 합니다.");
        }
    }

    private void validateReferenceId(String referenceId) {
        if (!StringUtils.hasText(referenceId)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "referenceId는 필수입니다.");
        }
    }

    private void applyCreditChange(User user, CreditTransactionType type, int amount) {
        if (type == CreditTransactionType.USE) {
            try {
                user.decreaseCredit(amount);
            } catch (IllegalArgumentException e) {
                throw new GeneralException(GeneralErrorCode.INSUFFICIENT_CREDIT, "크레딧이 부족합니다.");
            }
            return;
        }
        user.increaseCredit(amount);
    }

    private int resolveTransactionAmount(CreditTransactionType type, int amount) {
        return type == CreditTransactionType.USE ? -amount : amount;
    }

    private void saveTransaction(
            User user,
            CreditTransactionType type,
            int amount,
            String description,
            String referenceId
    ) {
        creditTransactionRepository.save(CreditTransaction.create(
                user,
                type,
                amount,
                user.getCredit(),
                description,
                referenceId
        ));
    }

    private User getManagedUser(User user) {
        if (user == null || user.getId() == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 정보가 누락되었습니다.");
        }
        User managedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND,
                        "해당 유저를 찾을 수 없습니다. userId=" + user.getId()
                ));
        // 상위 트랜잭션이 User를 먼저 조회했다면 비관적 락을 얻어도 1차 캐시의
        // 오래된 잔액이 유지될 수 있다. 락을 보유한 상태에서 최신 DB 값으로 갱신한다.
        entityManager.refresh(managedUser);
        return managedUser;
    }
}
