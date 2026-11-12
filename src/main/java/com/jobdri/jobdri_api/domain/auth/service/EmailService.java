package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String AUTH_CODE_PREFIX = "AuthCode:";
    private static final String VERIFIED_EMAIL_PREFIX = "VerifiedEmail:";
    private static final String AUTH_CODE_COOLDOWN_PREFIX = "AuthCodeCooldown:";
    private static final String AUTH_CODE_IP_COOLDOWN_PREFIX = "AuthCodeIpCooldown:";
    private static final String AUTH_CODE_ATTEMPT_PREFIX = "AuthCodeAttempt:";
    private static final String VERIFIED_FLAG = "true";
    private static final long AUTH_CODE_EXPIRATION_MINUTES = 5L;
    private static final long VERIFIED_EMAIL_EXPIRATION_MINUTES = 10L;
    private static final long AUTH_CODE_COOLDOWN_MINUTES = 1L;
    private static final long AUTH_CODE_IP_COOLDOWN_SECONDS = 30L;
    static final long MAX_AUTH_CODE_ATTEMPTS = 5L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final AsyncEmailSender asyncEmailSender;

    public void sendVerificationCode(String email) {
        if (isAuthCodeIpRateLimited() || !acquireAuthCodeCooldown(email)) {
            return;
        }

        String authCode = createAuthCode();

        redisTemplate.opsForValue().set(
                getAuthCodeKey(email),
                authCode,
                AUTH_CODE_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
        redisTemplate.delete(getAuthCodeAttemptKey(email));

        asyncEmailSender.sendVerificationCodeMail(email, authCode);
        log.info("[EmailService] 인증번호 발송 요청 등록: {}", email);
    }

    public void verifyCode(String email, String code) {
        String redisKey = getAuthCodeKey(email);
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_AUTH_CODE, "인증번호가 만료되었거나 존재하지 않습니다.");
        }

        // 6자리 코드 무차별 대입을 막기 위해 비교 전에 시도 횟수를 먼저 센다.
        if (recordAttempt(email) > MAX_AUTH_CODE_ATTEMPTS) {
            redisTemplate.delete(redisKey);
            redisTemplate.delete(getAuthCodeAttemptKey(email));
            throw new GeneralException(
                    GeneralErrorCode.AUTH_CODE_ATTEMPTS_EXCEEDED,
                    "인증번호 확인 횟수를 초과했습니다. 인증번호를 다시 요청해주세요."
            );
        }

        if (!MessageDigest.isEqual(
                storedCode.getBytes(StandardCharsets.UTF_8),
                String.valueOf(code).getBytes(StandardCharsets.UTF_8)
        )) {
            throw new GeneralException(GeneralErrorCode.INVALID_AUTH_CODE, "인증번호가 일치하지 않습니다.");
        }

        redisTemplate.delete(redisKey);
        redisTemplate.delete(getAuthCodeAttemptKey(email));
        redisTemplate.opsForValue().set(
                getVerifiedEmailKey(email),
                VERIFIED_FLAG,
                VERIFIED_EMAIL_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public void checkEmailVerified(String email) {
        String verifiedFlag = redisTemplate.opsForValue().get(getVerifiedEmailKey(email));

        if (!VERIFIED_FLAG.equals(verifiedFlag)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_VERIFIED, "이메일 인증이 필요합니다.");
        }
    }

    public void deleteVerifiedEmailFlag(String email) {
        redisTemplate.delete(getVerifiedEmailKey(email));
    }

    private String getAuthCodeKey(String email) {
        return AUTH_CODE_PREFIX + email;
    }

    private String getVerifiedEmailKey(String email) {
        return VERIFIED_EMAIL_PREFIX + email;
    }

    private String getAuthCodeAttemptKey(String email) {
        return AUTH_CODE_ATTEMPT_PREFIX + email;
    }

    private long recordAttempt(String email) {
        String attemptKey = getAuthCodeAttemptKey(email);
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts == null) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE, "인증번호 확인 요청을 처리할 수 없습니다.");
        }
        if (attempts == 1L) {
            redisTemplate.expire(attemptKey, AUTH_CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        }
        return attempts;
    }

    private String getAuthCodeCooldownKey(String email) {
        return AUTH_CODE_COOLDOWN_PREFIX + email;
    }

    private String getAuthCodeIpCooldownKey(String clientIp) {
        return AUTH_CODE_IP_COOLDOWN_PREFIX + sha256(clientIp);
    }

    private boolean acquireAuthCodeCooldown(String email) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                getAuthCodeCooldownKey(email),
                "1",
                AUTH_CODE_COOLDOWN_MINUTES,
                TimeUnit.MINUTES
        );
        return Boolean.TRUE.equals(acquired);
    }

    private boolean isAuthCodeIpRateLimited() {
        String clientIp = MDC.get(LoggingMdcKeys.CLIENT_IP);
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                getAuthCodeIpCooldownKey(clientIp),
                "1",
                AUTH_CODE_IP_COOLDOWN_SECONDS,
                TimeUnit.SECONDS
        );
        return !Boolean.TRUE.equals(acquired);
    }

    private String createAuthCode() {
        int number = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(number);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "이메일 인증 요청 처리에 실패했습니다.");
        }
    }
}
