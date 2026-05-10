package com.jobdri.jobdri_api.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncEmailSender {

    private final JavaMailSender mailSender;

    @Value("${mail.from:${spring.mail.username:}}")
    private String fromAddress;

    @Async
    @Retryable(
            retryFor = MailException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void sendVerificationCodeMail(String email, String authCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[jobdri] 회원가입 인증번호 안내");
        message.setText("인증번호는 [" + authCode + "] 입니다.");
        mailSender.send(message);

        log.info("[AsyncEmailSender] 인증 메일 발송 성공: {}", email);
    }

    @Recover
    public void recover(MailException exception, String email, String authCode) {
        log.error("[AsyncEmailSender] 인증 메일 발송 최종 실패: email={}, authCode={}", email, authCode, exception);
    }
}
