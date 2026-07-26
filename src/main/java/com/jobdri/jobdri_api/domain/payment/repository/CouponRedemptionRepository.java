package com.jobdri.jobdri_api.domain.payment.repository;

import com.jobdri.jobdri_api.domain.payment.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    Optional<CouponRedemption> findByUserIdAndCouponCode(Long userId, String couponCode);
}
