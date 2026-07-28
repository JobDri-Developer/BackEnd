package com.jobdri.jobdri_api.domain.payment.entity;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(unique = true)
    private String paymentKey;

    @Column(unique = true)
    private String payToken;

    @Column(length = 500)
    private String checkoutPage;

    @Column(length = 50)
    private String tossStatus;

    @Column(nullable = false)
    private String planCode;

    @Column(nullable = false)
    private int creditAmount;

    @Column(nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private LocalDateTime approvedAt;

    private LocalDateTime callbackReceivedAt;

    private LocalDateTime lastStatusCheckedAt;

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
                .build();
    }

    public void markProcessing(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.PROCESSING;
    }

    public void attachTossPayPayment(String payToken, String checkoutPage) {
        this.payToken = payToken;
        this.checkoutPage = checkoutPage;
        this.tossStatus = PaymentStatus.PENDING.name();
    }

    public void complete(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = LocalDateTime.now();
    }

    public void completeByTossPay(String tossStatus) {
        this.tossStatus = tossStatus;
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = LocalDateTime.now();
        this.callbackReceivedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void failByTossPay(String tossStatus) {
        this.tossStatus = tossStatus;
        this.status = PaymentStatus.FAILED;
        this.callbackReceivedAt = LocalDateTime.now();
    }

    public void markUnknown() {
        this.status = PaymentStatus.UNKNOWN;
    }

    public void markTossPayUnknown() {
        this.status = PaymentStatus.UNKNOWN;
    }

    public void updateTossStatus(String tossStatus) {
        this.tossStatus = tossStatus;
        this.callbackReceivedAt = LocalDateTime.now();
    }

    public void markStatusChecked(String tossStatus) {
        this.tossStatus = tossStatus;
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public boolean belongsTo(Long userId) {
        return user != null && user.getId() != null && user.getId().equals(userId);
    }

    public boolean hasPaymentKey(String paymentKey) {
        return this.paymentKey != null && this.paymentKey.equals(paymentKey);
    }

    public boolean hasPayToken(String payToken) {
        return this.payToken != null && this.payToken.equals(payToken);
    }
}
