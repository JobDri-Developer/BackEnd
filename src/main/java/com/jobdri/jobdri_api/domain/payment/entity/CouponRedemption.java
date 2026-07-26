package com.jobdri.jobdri_api.domain.payment.entity;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "coupon_redemptions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coupon_redemptions_user_coupon_code",
                        columnNames = {"user_id", "coupon_code"}
                )
        }
)
public class CouponRedemption extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, name = "coupon_code", length = 14)
    private String couponCode;

    @Column(nullable = false)
    private int creditAmount;

    public static CouponRedemption create(User user, String couponCode, int creditAmount) {
        return CouponRedemption.builder()
                .user(user)
                .couponCode(couponCode)
                .creditAmount(creditAmount)
                .build();
    }
}
