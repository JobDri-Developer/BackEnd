package com.jobdri.jobdri_api.domain.payment.entity;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.type.PaymentStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentProviderType provider;

    @Column(unique = true)
    private String externalPaymentId;

    @Column
    private String externalTransactionId;

    @Column(length = 50)
    private String externalStatus;

    @Column(length = 255)
    private String refundReason;

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
        return createPending(user, content, orderId, planCode, creditAmount, price, PaymentProviderType.TOSS_PAY_DIRECT);
    }

    public static Payment createPending(
            User user,
            String content,
            String orderId,
            String planCode,
            int creditAmount,
            int price,
            PaymentProviderType provider
    ) {
        return Payment.builder()
                .user(user)
                .content(content)
                .orderId(orderId)
                .planCode(planCode)
                .creditAmount(creditAmount)
                .price(price)
                .provider(PaymentProviderType.fromNullable(provider))
                .status(PaymentStatus.PENDING)
                .build();
    }

    public void markProcessing(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.PROCESSING;
    }

    public void attachTossPayPayment(String payToken, String checkoutPage) {
        this.provider = PaymentProviderType.TOSS_PAY_DIRECT;
        this.payToken = payToken;
        this.checkoutPage = checkoutPage;
        this.tossStatus = PaymentStatus.PENDING.name();
    }

    public void attachPortOnePayment(String externalPaymentId) {
        this.provider = PaymentProviderType.PORTONE;
        this.externalPaymentId = externalPaymentId;
        this.externalStatus = PaymentStatus.PENDING.name();
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

    public void completeByPortOne(String externalStatus, String externalTransactionId) {
        this.externalStatus = externalStatus;
        this.externalTransactionId = externalTransactionId;
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = LocalDateTime.now();
        this.callbackReceivedAt = LocalDateTime.now();
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void failByTossPay(String tossStatus) {
        this.tossStatus = tossStatus;
        this.status = PaymentStatus.FAILED;
        this.callbackReceivedAt = LocalDateTime.now();
    }

    public void failByPortOne(String externalStatus, String externalTransactionId) {
        this.externalStatus = externalStatus;
        this.externalTransactionId = externalTransactionId;
        this.status = PaymentStatus.FAILED;
        this.callbackReceivedAt = LocalDateTime.now();
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public void markUnknown() {
        this.status = PaymentStatus.UNKNOWN;
    }

    public void markTossPayUnknown() {
        this.status = PaymentStatus.UNKNOWN;
    }

    public void markTossPayCreationUnknown(String payToken, String checkoutPage) {
        this.payToken = payToken;
        this.checkoutPage = checkoutPage;
        this.status = PaymentStatus.UNKNOWN;
    }

    public void updateTossStatus(String tossStatus) {
        this.tossStatus = tossStatus;
        this.callbackReceivedAt = LocalDateTime.now();
    }

    public void updatePortOneStatus(String externalStatus, String externalTransactionId) {
        this.externalStatus = externalStatus;
        this.externalTransactionId = externalTransactionId;
        this.callbackReceivedAt = LocalDateTime.now();
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public void markPortOneUnknown(String externalStatus, String externalTransactionId) {
        this.externalStatus = externalStatus;
        this.externalTransactionId = externalTransactionId;
        this.status = PaymentStatus.UNKNOWN;
        this.callbackReceivedAt = LocalDateTime.now();
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public void startRefund(String refundReason) {
        this.refundReason = refundReason;
    }

    public void clearRefundReason() {
        this.refundReason = null;
    }

    public boolean isRefundInProgress() {
        return this.refundReason != null && this.status == PaymentStatus.COMPLETED;
    }

    public void refundByTossPay(String tossStatus, String refundReason) {
        validateRefundableStatus();
        this.tossStatus = tossStatus;
        this.refundReason = refundReason;
        this.status = PaymentStatus.REFUNDED;
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public void refundByPortOne(String externalStatus, String refundReason) {
        validateRefundableStatus();
        this.externalStatus = externalStatus;
        this.refundReason = refundReason;
        this.status = PaymentStatus.REFUNDED;
        this.lastStatusCheckedAt = LocalDateTime.now();
    }

    public void completeByProviderStatus(String providerStatus, String providerTransactionId) {
        switch (getProviderOrDefault()) {
            case TOSS_PAY_DIRECT -> completeByTossPay(providerStatus);
            case PORTONE -> completeByPortOne(providerStatus, providerTransactionId);
            default -> throw unsupportedProvider("결제 완료");
        }
    }

    public void failByProviderStatus(String providerStatus, String providerTransactionId) {
        switch (getProviderOrDefault()) {
            case TOSS_PAY_DIRECT -> failByTossPay(providerStatus);
            case PORTONE -> failByPortOne(providerStatus, providerTransactionId);
            default -> throw unsupportedProvider("결제 실패");
        }
    }

    public void markUnknownByProviderStatus(String providerStatus, String providerTransactionId) {
        switch (getProviderOrDefault()) {
            case TOSS_PAY_DIRECT -> {
                markTossPayUnknown();
                updateTossStatus(providerStatus);
            }
            case PORTONE -> markPortOneUnknown(providerStatus, providerTransactionId);
            default -> throw unsupportedProvider("결제 상태 미확정");
        }
    }

    public void updateProviderStatus(String providerStatus, String providerTransactionId) {
        switch (getProviderOrDefault()) {
            case TOSS_PAY_DIRECT -> updateTossStatus(providerStatus);
            case PORTONE -> updatePortOneStatus(providerStatus, providerTransactionId);
            default -> throw unsupportedProvider("결제 상태 갱신");
        }
    }

    public void refundByProviderStatus(String providerStatus, String refundReason) {
        switch (getProviderOrDefault()) {
            case TOSS_PAY_DIRECT -> refundByTossPay(providerStatus, refundReason);
            case PORTONE -> refundByPortOne(providerStatus, refundReason);
            default -> throw unsupportedProvider("결제 환불");
        }
    }

    private void validateRefundableStatus() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 환불 상태로 변경할 수 있습니다.");
        }
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

    public PaymentProviderType getProviderOrDefault() {
        return PaymentProviderType.fromNullable(provider);
    }

    public boolean isProvider(PaymentProviderType provider) {
        return getProviderOrDefault() == provider;
    }

    private IllegalStateException unsupportedProvider(String action) {
        return new IllegalStateException("지원하지 않는 결제 제공자입니다. action=" + action + ", provider=" + getProviderOrDefault());
    }
}
