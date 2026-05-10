package com.jobdri.jobdri_api.domain.auth.handler;

import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.auth.security.CustomOAuth2User;
import com.jobdri.jobdri_api.domain.auth.service.AuthService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/redirect}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        User user = principal.user();

        LoginResponse tokenResponse = authService.issueTokens(user);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("accessToken", tokenResponse.accessToken())
                .queryParam("refreshToken", tokenResponse.refreshToken())
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
