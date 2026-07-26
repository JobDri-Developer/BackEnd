package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AsyncEmailSender asyncEmailSender;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MDC.clear();
        emailService = new EmailService(redisTemplate, asyncEmailSender);
    }

    @Test
    @DisplayName("이메일 인증번호 요청은 쿨다운을 선점한 뒤 인증번호를 저장하고 메일을 발송한다")
    void sendVerificationCodeStoresCodeAndSendsMail() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("AuthCodeCooldown:user@example.com"),
                eq("1"),
                eq(1L),
                eq(TimeUnit.MINUTES)
        )).thenReturn(true);

        emailService.sendVerificationCode("user@example.com");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("AuthCode:user@example.com"),
                codeCaptor.capture(),
                eq(5L),
                eq(TimeUnit.MINUTES)
        );
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        verify(asyncEmailSender).sendVerificationCodeMail(eq("user@example.com"), eq(codeCaptor.getValue()));
    }

    @Test
    @DisplayName("이메일 인증번호 반복 요청은 쿨다운 동안 메일을 한 번만 발송한다")
    void sendVerificationCodeSendsMailOnlyOnceDuringCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("AuthCodeCooldown:user@example.com"),
                eq("1"),
                eq(1L),
                eq(TimeUnit.MINUTES)
        )).thenReturn(true, false);

        emailService.sendVerificationCode("user@example.com");
        emailService.sendVerificationCode("user@example.com");

        verify(asyncEmailSender, times(1)).sendVerificationCodeMail(eq("user@example.com"), anyString());
        verify(valueOperations, times(1)).set(
                eq("AuthCode:user@example.com"),
                anyString(),
                eq(5L),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    @DisplayName("IP 쿨다운에 걸린 인증번호 요청은 계정 쿨다운과 메일 발송을 수행하지 않는다")
    void sendVerificationCodeDoesNothingWhenIpRateLimited() {
        MDC.put(LoggingMdcKeys.CLIENT_IP, "203.0.113.20");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("1"),
                eq(30L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(false);

        emailService.sendVerificationCode("user@example.com");

        verify(valueOperations, never()).setIfAbsent(
                eq("AuthCodeCooldown:user@example.com"),
                eq("1"),
                eq(1L),
                eq(TimeUnit.MINUTES)
        );
        verify(asyncEmailSender, never()).sendVerificationCodeMail(anyString(), anyString());
    }

    @Test
    @DisplayName("인증번호 확인은 성공 시 인증번호를 삭제하고 인증 완료 플래그를 저장한다")
    void verifyCodeStoresVerifiedFlagAndDeletesCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("AuthCode:user@example.com")).thenReturn("123456");

        emailService.verifyCode("user@example.com", "123456");

        verify(redisTemplate).delete("AuthCode:user@example.com");
        verify(valueOperations).set(
                "VerifiedEmail:user@example.com",
                "true",
                10L,
                TimeUnit.MINUTES
        );
    }

    @Test
    @DisplayName("회원가입 전 이메일 인증 완료 플래그가 없으면 실패한다")
    void checkEmailVerifiedRejectsMissingFlag() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("VerifiedEmail:user@example.com")).thenReturn(null);

        assertThatThrownBy(() -> emailService.checkEmailVerified("user@example.com"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.EMAIL_NOT_VERIFIED);
    }
}
