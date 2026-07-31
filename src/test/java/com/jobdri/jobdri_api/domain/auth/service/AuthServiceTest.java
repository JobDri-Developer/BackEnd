package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.audit.service.AuditLogService;
import com.jobdri.jobdri_api.domain.auth.dto.request.LoginRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.PasswordResetConfirmationRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.PasswordResetEmailRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.ReissueTokenRequest;
import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.user.entity.SocialType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.jwt.JwtUtil;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import io.jsonwebtoken.Claims;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private EmailService emailService;

    @Mock
    private AsyncEmailSender asyncEmailSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MDC.clear();
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                emailService,
                asyncEmailSender,
                redisTemplate,
                auditLogService
        );
    }

    @Test
    @DisplayName("로컬 로그인 성공 시 refresh token 저장 후 로그인 성공 audit 로그를 남긴다")
    void loginStoresRefreshTokenAndRecordsAuditLog() {
        User user = localUser(1L, "local@example.com");
        when(userRepository.findByEmail("local@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtUtil.createAccessToken(user.getEmail(), user.getId(), user.getRole())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(user.getEmail())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenTime()).thenReturn(604_800_000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginResponse response = authService.login(new LoginRequest("local@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(valueOperations).set(
                "RefreshToken:1",
                "refresh-token",
                604_800_000L,
                TimeUnit.MILLISECONDS
        );
        verify(auditLogService).record(
                eq(user),
                eq("LOGIN_SUCCESS"),
                eq("USER"),
                eq(1L),
                isNull(),
                eq(Map.of("loginMethod", "LOCAL"))
        );
    }

    @Test
    @DisplayName("소셜 로그인 토큰 발급도 Google 로그인 성공 audit 로그를 남긴다")
    void issueTokensRecordsGoogleLoginAuditLog() {
        User user = User.createSocialUser(
                "구글 사용자",
                "google@example.com",
                "encoded-password",
                SocialType.GOOGLE,
                "google-id"
        );
        ReflectionTestUtils.setField(user, "id", 2L);
        when(jwtUtil.createAccessToken(user.getEmail(), user.getId(), user.getRole())).thenReturn("google-access-token");
        when(jwtUtil.createRefreshToken(user.getEmail())).thenReturn("google-refresh-token");
        when(jwtUtil.getRefreshTokenTime()).thenReturn(604_800_000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginResponse response = authService.issueTokens(user);

        assertThat(response.accessToken()).isEqualTo("google-access-token");
        assertThat(response.refreshToken()).isEqualTo("google-refresh-token");
        verify(auditLogService).record(
                eq(user),
                eq("LOGIN_SUCCESS"),
                eq("USER"),
                eq(2L),
                isNull(),
                eq(Map.of("loginMethod", "GOOGLE"))
        );
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 로컬 계정에만 토큰을 저장하고 메일을 발송한다")
    void sendPasswordResetEmailStoresTokenAndSendsMailForLocalUser() {
        User user = localUser(1L, "reset@example.com");
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("PasswordResetCooldown:1"),
                eq("1"),
                eq(5L),
                eq(TimeUnit.MINUTES)
        )).thenReturn(true);

        authService.sendPasswordResetEmail(new PasswordResetEmailRequest("reset@example.com"));

        ArgumentCaptor<String> tokenKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                tokenKeyCaptor.capture(),
                eq("1"),
                eq(30L),
                eq(TimeUnit.MINUTES)
        );
        assertThat(tokenKeyCaptor.getValue()).startsWith("PasswordResetToken:");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncEmailSender).sendPasswordResetMail(eq("reset@example.com"), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 계정 쿨다운 동안 메일을 한 번만 발송한다")
    void sendPasswordResetEmailSendsMailOnlyOnceDuringAccountCooldown() {
        User user = localUser(1L, "reset@example.com");
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("PasswordResetCooldown:1"),
                eq("1"),
                eq(5L),
                eq(TimeUnit.MINUTES)
        )).thenReturn(true, false);

        authService.sendPasswordResetEmail(new PasswordResetEmailRequest("reset@example.com"));
        authService.sendPasswordResetEmail(new PasswordResetEmailRequest("reset@example.com"));

        verify(asyncEmailSender, times(1)).sendPasswordResetMail(eq("reset@example.com"), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 IP 쿨다운에 걸리면 계정 조회 없이 성공 흐름으로 종료한다")
    void sendPasswordResetEmailDoesNothingWhenIpRateLimited() {
        MDC.put(LoggingMdcKeys.CLIENT_IP, "203.0.113.10");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("1"),
                eq(60L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(false);

        authService.sendPasswordResetEmail(new PasswordResetEmailRequest("reset@example.com"));

        verifyNoInteractions(userRepository, asyncEmailSender);
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 없는 이메일이어도 성공 흐름으로 끝내고 계정 존재 여부를 숨긴다")
    void sendPasswordResetEmailDoesNothingForUnknownEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.sendPasswordResetEmail(new PasswordResetEmailRequest("unknown@example.com"));

        verifyNoInteractions(redisTemplate, asyncEmailSender);
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 소셜 계정이면 토큰을 만들지 않는다")
    void sendPasswordResetEmailDoesNothingForSocialUser() {
        User user = User.createSocialUser(
                "구글 사용자",
                "google@example.com",
                "encoded-password",
                SocialType.GOOGLE,
                "google-id"
        );
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(user));

        authService.sendPasswordResetEmail(new PasswordResetEmailRequest("google@example.com"));

        verifyNoInteractions(redisTemplate, asyncEmailSender);
    }

    @Test
    @DisplayName("비밀번호 재설정은 토큰이 없으면 실패한다")
    void resetPasswordRejectsInvalidToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword(
                new PasswordResetConfirmationRequest("invalid-token", "newPass123")
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);

        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("비밀번호 재설정은 비밀번호를 변경하고 기존 refresh token을 무효화한다")
    void resetPasswordUpdatesPasswordAndDeletesRefreshToken() {
        User user = localUser(1L, "reset@example.com");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("1");
        when(valueOperations.setIfAbsent(
                eq("ReissueLock:1"),
                eq("password-reset"),
                eq(3L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded-new-password");

        authService.resetPassword(new PasswordResetConfirmationRequest("valid-token", "newPass123"));

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        ArgumentCaptor<String> deleteKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(3)).delete(deleteKeyCaptor.capture());
        List<String> deletedKeys = deleteKeyCaptor.getAllValues();
        assertThat(deletedKeys).anyMatch(key -> key.startsWith("PasswordResetToken:"));
        assertThat(deletedKeys).contains("RefreshToken:1");
        assertThat(deletedKeys).contains("ReissueLock:1");
    }

    @Test
    @DisplayName("비밀번호 재설정 중 reissue가 끼어들어도 stale refresh token은 저장되지 않는다")
    void resetPasswordSerializesRefreshTokenDeletionAgainstReissue() {
        User user = localUser(1L, "reset@example.com");
        Claims claims = mock(Claims.class);
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(jwtUtil.getClaimsFromExpiredToken("expired-access-token")).thenReturn(claims);
        when(jwtUtil.validateToken("old-refresh-token")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("1");
        when(valueOperations.setIfAbsent(
                eq("ReissueLock:1"),
                eq("password-reset"),
                eq(3L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(valueOperations.setIfAbsent(
                eq("ReissueLock:1"),
                eq("old-refresh-token"),
                eq(3L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass123")).thenAnswer(invocation -> {
            assertThatThrownBy(() -> authService.reissueToken(
                    new ReissueTokenRequest("expired-access-token", "old-refresh-token")
            ))
                    .isInstanceOf(GeneralException.class)
                    .extracting("code")
                    .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
            return "encoded-new-password";
        });

        authService.resetPassword(new PasswordResetConfirmationRequest("valid-token", "newPass123"));

        verify(valueOperations, never()).set(eq("RefreshToken:1"), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(redisTemplate).delete("RefreshToken:1");
    }

    private User localUser(Long id, String email) {
        User user = User.signup("테스트 사용자", email, "encoded-password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
