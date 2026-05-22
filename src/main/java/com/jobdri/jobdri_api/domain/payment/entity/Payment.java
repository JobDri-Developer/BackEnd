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
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String content;

    @Column(unique = true)
    private String orderId;

    @Column(unique = true)
    private String paymentKey;

    @Column
    private String planCode;

    @Column(nullable = false)
    private int creditAmount;

    @Column(nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    public static Payment createPending(
            User user,
            String content,
            String orderId,
            String planCode,
            int creditAmount,
            int price
    ) {
        return Payment.builder()
                .user(user)
                .content(content)
                .orderId(orderId)
                .planCode(planCode)
                .creditAmount(creditAmount)
                .price(price)
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void complete(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}
