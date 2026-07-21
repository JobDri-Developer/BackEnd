package com.jobdri.jobdri_api.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class RequestContextLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String APPLICATION_LOG_TYPE = "application";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();

        try {
            String requestId = resolveRequestId(request);

            MDC.put(LoggingMdcKeys.REQUEST_ID, requestId);
            MDC.put(LoggingMdcKeys.METHOD, request.getMethod());
            MDC.put(LoggingMdcKeys.URI, request.getRequestURI());
            MDC.put(LoggingMdcKeys.CLIENT_IP, resolveClientIp(request));
            MDC.put(LoggingMdcKeys.LOG_TYPE, APPLICATION_LOG_TYPE);

            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            if (previousContext != null && !previousContext.isEmpty()) {
                MDC.setContextMap(previousContext);
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
