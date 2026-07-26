package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.dto.request.PasswordResetConfirmationRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.PasswordResetEmailRequest;
import com.jobdri.jobdri_api.domain.user.entity.SocialType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                emailService,
                asyncEmailSender,
                redisTemplate
        );
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 로컬 계정에만 토큰을 저장하고 메일을 발송한다")
    void sendPasswordResetEmailStoresTokenAndSendsMailForLocalUser() {
        User user = localUser(1L, "reset@example.com");
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded-new-password");

        authService.resetPassword(new PasswordResetConfirmationRequest("valid-token", "newPass123"));

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        ArgumentCaptor<String> deleteKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(2)).delete(deleteKeyCaptor.capture());
        List<String> deletedKeys = deleteKeyCaptor.getAllValues();
        assertThat(deletedKeys).anyMatch(key -> key.startsWith("PasswordResetToken:"));
        assertThat(deletedKeys).contains("RefreshToken:1");
    }

    private User localUser(Long id, String email) {
        User user = User.signup("테스트 사용자", email, "encoded-password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
