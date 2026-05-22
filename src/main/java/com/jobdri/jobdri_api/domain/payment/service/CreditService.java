package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.entity.CreditTransaction;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int charge(User user, int amount, String description, String referenceId) {
        User managedUser = getManagedUser(user);
        managedUser.increaseCredit(amount);
        saveTransaction(managedUser, CreditTransactionType.CHARGE, amount, description, referenceId);
        return managedUser.getCredit();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int use(User user, int amount, String description, String referenceId) {
        User managedUser = getManagedUser(user);
        try {
            managedUser.decreaseCredit(amount);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.INSUFFICIENT_CREDIT, "크레딧이 부족합니다.");
        }
        saveTransaction(managedUser, CreditTransactionType.USE, -amount, description, referenceId);
        return managedUser.getCredit();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int refund(User user, int amount, String description, String referenceId) {
        User managedUser = getManagedUser(user);
        managedUser.increaseCredit(amount);
        saveTransaction(managedUser, CreditTransactionType.REFUND, amount, description, referenceId);
        return managedUser.getCredit();
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
        return userRepository.findById(user.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND,
                        "해당 유저를 찾을 수 없습니다. userId=" + user.getId()
                ));
    }
}
