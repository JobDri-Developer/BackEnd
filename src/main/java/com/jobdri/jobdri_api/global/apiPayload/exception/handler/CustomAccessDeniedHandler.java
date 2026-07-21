package com.jobdri.jobdri_api.global.apiPayload.exception.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.logging.LoggingContext;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(@Nonnull HttpServletRequest request,
                       @Nonnull HttpServletResponse response,
                       @Nonnull AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        GeneralErrorCode errorCode = GeneralErrorCode.FORBIDDEN;
        try (var ignored = LoggingContext.with("auth.forbidden", errorCode)) {
            log.warn(
                    "Forbidden access detected: {} {} - {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    accessDeniedException.getMessage()
            );
        }
        ApiResponse<Object> apiResponse = ApiResponse.onFailure(errorCode, "해당 리소스에 대한 접근 권한이 없습니다.");

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
