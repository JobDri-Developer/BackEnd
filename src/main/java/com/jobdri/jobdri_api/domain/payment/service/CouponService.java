package com.jobdri.jobdri_api.domain.payment.service;

import com.jobdri.jobdri_api.domain.payment.dto.request.CouponRedeemRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.CouponRedeemResponse;
import com.jobdri.jobdri_api.domain.payment.entity.CouponRedemption;
import com.jobdri.jobdri_api.domain.payment.repository.CouponRedemptionRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private static final int COUPON_CREDIT_AMOUNT = 1;

    private final UserService userService;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CreditService creditService;

    @Value("${payment.coupon.code:}")
    private String couponCode;

    @Transactional
    public CouponRedeemResponse redeem(User user, CouponRedeemRequest request) {
        User validatedUser = userService.validateUser(user);
        String normalizedCouponCode = normalizeCouponCode(request.couponCode());
        validateCouponConfiguration();
        validateCouponCode(normalizedCouponCode);

        try {
            CouponRedemption redemption = couponRedemptionRepository.saveAndFlush(
                    CouponRedemption.create(validatedUser, normalizedCouponCode, COUPON_CREDIT_AMOUNT)
            );
            int creditBalance = creditService.coupon(
                    validatedUser,
                    COUPON_CREDIT_AMOUNT,
                    "쿠폰 등록",
                    "coupon-redemption-" + redemption.getId()
            );
            return new CouponRedeemResponse(
                    redemption.getCouponCode(),
                    redemption.getCreditAmount(),
                    creditBalance,
                    redemption.getCreatedAt()
            );
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(GeneralErrorCode.COUPON_ALREADY_REDEEMED, "이미 사용한 쿠폰입니다.");
        }
    }

    private void validateCouponConfiguration() {
        if (!StringUtils.hasText(couponCode)) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "쿠폰 설정이 올바르지 않습니다.");
        }
    }

    private void validateCouponCode(String inputCouponCode) {
        String configuredCouponCode = normalizeCouponCode(couponCode);
        if (!configuredCouponCode.equals(inputCouponCode)) {
            throw new GeneralException(GeneralErrorCode.COUPON_INVALID, "유효하지 않은 쿠폰입니다.");
        }
    }

    private String normalizeCouponCode(String rawCouponCode) {
        return rawCouponCode.trim().toUpperCase(Locale.ROOT);
    }
}
