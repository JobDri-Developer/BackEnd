package com.jobdri.jobdri_api.domain.payment.entity;

import com.jobdri.jobdri_api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "credit_transactions")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditTransactionType type;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int balanceAfter;

    @Column(nullable = false)
    private String description;

    private String referenceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static CreditTransaction create(
            User user,
            CreditTransactionType type,
            int amount,
            int balanceAfter,
            String description,
            String referenceId
    ) {
        return CreditTransaction.builder()
                .user(user)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .description(description)
                .referenceId(referenceId)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
