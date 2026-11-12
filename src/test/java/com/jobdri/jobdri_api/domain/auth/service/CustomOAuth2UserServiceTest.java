package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.security.CustomOAuth2User;
import com.jobdri.jobdri_api.domain.user.entity.SocialType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomOAuth2UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    @SuppressWarnings("unchecked")
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
    private final OAuth2UserRequest userRequest = mock(OAuth2UserRequest.class);

    private CustomOAuth2UserService service() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        return new CustomOAuth2UserService(userRepository, passwordEncoder, delegate);
    }

    @Test
    @DisplayName("Google이 이메일 소유를 확인하지 않은 계정은 로그인과 계정 연결을 거부한다")
    void rejectsUnverifiedGoogleEmail() {
        CustomOAuth2UserService service = service();
        when(delegate.loadUser(userRequest)).thenReturn(googleUser(false));

        assertThatThrownBy(() -> service.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception).getError().getErrorCode())
                .isEqualTo("unverified_email");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("email_verified 값이 없으면 확인되지 않은 이메일로 보고 거부한다")
    void rejectsMissingEmailVerifiedClaim() {
        CustomOAuth2UserService service = service();
        when(delegate.loadUser(userRequest)).thenReturn(googleUser(null));

        assertThatThrownBy(() -> service.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("확인된 Google 이메일은 신규 소셜 계정으로 가입한다")
    void createsSocialUserForVerifiedEmail() {
        CustomOAuth2UserService service = service();
        when(delegate.loadUser(userRequest)).thenReturn(googleUser(true));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomOAuth2User result = (CustomOAuth2User) service.loadUser(userRequest);

        assertThat(result.user().getEmail()).isEqualTo("user@example.com");
        assertThat(result.user().getSocialType()).isEqualTo(SocialType.GOOGLE);
    }

    private OAuth2User googleUser(Boolean emailVerified) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub");
        attributes.put("email", "user@example.com");
        attributes.put("name", "사용자");
        if (emailVerified != null) {
            attributes.put("email_verified", emailVerified);
        }
        return new DefaultOAuth2User(List.of(), attributes, "sub");
    }
}
