package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String AUTH_CODE_PREFIX = "AuthCode:";
    private static final long AUTH_CODE_EXPIRATION_MINUTES = 5L;
    private static final String VERIFIED_EMAIL_PREFIX = "VerifiedEmail:";
    private static final long VERIFIED_EMAIL_EXPIRATION_MINUTES = 10L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${mail.from:${spring.mail.username:}}")
    private String fromAddress;

    public void sendVerificationCode(String email) {
        String authCode = createAuthCode();

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject("[jobdri] 회원가입 인증번호 안내");
            message.setText("인증번호는 [" + authCode + "] 입니다.");
            mailSender.send(message);
        } catch (MailException e) {
            log.error("이메일 발송 실패: {}", email, e);
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다.");
        }

        log.info("[EmailService] 인증번호 발송 요청: {} / 인증번호: {}", email, authCode);

        redisTemplate.opsForValue().set(
                AUTH_CODE_PREFIX + email,
                authCode,
                AUTH_CODE_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public void verifyCode(String email, String code) {
        String redisKey = AUTH_CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_AUTH_CODE, "인증번호가 만료되었거나 존재하지 않습니다.");
        }

        if (!storedCode.equals(code)) {
            throw new GeneralException(GeneralErrorCode.INVALID_AUTH_CODE, "인증번호가 일치하지 않습니다.");
        }

        redisTemplate.delete(redisKey);
        redisTemplate.opsForValue().set(
                VERIFIED_EMAIL_PREFIX + email,
                "true",
                VERIFIED_EMAIL_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public void checkEmailVerified(String email) {
        String verifiedFlag = redisTemplate.opsForValue().get(VERIFIED_EMAIL_PREFIX + email);

        if (!"true".equals(verifiedFlag)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_VERIFIED, "이메일 인증이 필요합니다.");
        }
    }

    public void deleteVerifiedEmailFlag(String email) {
        redisTemplate.delete(VERIFIED_EMAIL_PREFIX + email);
    }

    private String createAuthCode() {
        int number = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(number);
    }
}
