package com.jobdri.jobdri_api.domain.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortOneWebhookRateLimitFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH = "/api/payments/portone/webhook";
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, ArrayDeque<Long>> requestsByClient = new ConcurrentHashMap<>();
    private final Clock clock;

    public PortOneWebhookRateLimitFilter() {
        this(Clock.systemUTC());
    }

    PortOneWebhookRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!allowRequest(resolveClientIp(request))) {
            response.setStatus(429);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowRequest(String clientIp) {
        long now = clock.millis();
        ArrayDeque<Long> timestamps = requestsByClient.computeIfAbsent(clientIp, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MILLIS) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String clientIp = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(clientIp)) {
                return clientIp;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
