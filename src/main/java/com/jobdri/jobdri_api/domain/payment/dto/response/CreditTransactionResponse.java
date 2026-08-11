package com.jobdri.jobdri_api.domain.payment.dto.response;

import com.jobdri.jobdri_api.domain.payment.entity.CreditTransaction;
import com.jobdri.jobdri_api.domain.payment.type.CreditTransactionType;

import java.time.LocalDateTime;

public record CreditTransactionResponse(
        Long transactionId,
        CreditTransactionType type,
        int amount,
        int balanceAfter,
        String description,
        String referenceId,
        LocalDateTime createdAt
) {
    public static CreditTransactionResponse from(CreditTransaction transaction) {
        return new CreditTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}
