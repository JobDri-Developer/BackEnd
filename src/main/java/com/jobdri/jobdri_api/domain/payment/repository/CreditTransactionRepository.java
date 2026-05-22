package com.jobdri.jobdri_api.domain.payment.repository;

import com.jobdri.jobdri_api.domain.payment.entity.CreditTransaction;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
    List<CreditTransaction> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<CreditTransaction> findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, CreditTransactionType type);
}
