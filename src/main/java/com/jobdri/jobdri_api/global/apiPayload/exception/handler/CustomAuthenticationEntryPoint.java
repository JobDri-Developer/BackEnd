package com.jobdri.jobdri_api.global.apiPayload.exception.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j(topic = "AuthenticationEntryPoint")
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        GeneralErrorCode errorCode = GeneralErrorCode.MISSING_AUTH_INFO;
        try (var ignored = LoggingContext.with("auth.unauthorized", errorCode)) {
            log.warn(
                    "Unauthorized access detected: {} {} - {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    authException.getMessage()
            );
        }

        ApiResponse<Void> apiResponse = ApiResponse.onFailure(errorCode, errorCode.getMessage());

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
