package com.jobdri.jobdri_api.domain.auth.handler;

import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.auth.security.CustomOAuth2User;
import com.jobdri.jobdri_api.domain.auth.service.AuthService;
import com.jobdri.jobdri_api.domain.auth.service.OAuth2LoginCodeService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2AuthenticationSuccessHandlerTest {

    private final AuthService authService = mock(AuthService.class);
    private final OAuth2LoginCodeService oAuth2LoginCodeService = mock(OAuth2LoginCodeService.class);
    private final OAuth2AuthenticationSuccessHandler handler =
            new OAuth2AuthenticationSuccessHandler(authService, oAuth2LoginCodeService);

    @Test
    @DisplayName("소셜 로그인 성공 시 토큰 대신 일회용 코드만 redirect URL에 담는다")
    void redirectsWithOneTimeCodeInsteadOfTokens() throws Exception {
        ReflectionTestUtils.setField(handler, "frontendRedirectUri", "http://localhost:3000/oauth2/redirect");
        User user = User.authenticatedPrincipal(1L, "user@example.com", UserRole.USER);
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-sub"), List.of());
        LoginResponse tokens = new LoginResponse("access-token", "refresh-token");
        when(authService.issueTokens(user)).thenReturn(tokens);
        when(oAuth2LoginCodeService.issue(tokens)).thenReturn("one-time-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new TestingAuthenticationToken(principal, null)
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth2/redirect?code=one-time-code")
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }
}
