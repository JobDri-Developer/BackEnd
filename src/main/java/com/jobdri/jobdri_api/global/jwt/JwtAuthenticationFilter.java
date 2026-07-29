package com.jobdri.jobdri_api.global.jwt;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.metrics.AuthRedisMetricsRecorder;
import com.jobdri.jobdri_api.global.security.UserDetailsServiceImpl;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "JWT 검증 및 인가")
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "Blacklist:";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final AuthRedisMetricsRecorder authRedisMetricsRecorder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String tokenValue = request.getHeader(JwtUtil.AUTHORIZATION_HEADER);

        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(JwtUtil.BEARER_PREFIX)) {
            String token = jwtUtil.substringToken(tokenValue);

            if (isBlacklisted(token)) {
                log.info("블랙리스트 처리된 액세스 토큰입니다.");
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtUtil.validateToken(token)) {
                Claims claims = jwtUtil.getClaimsFromToken(token);
                String email = jwtUtil.getEmailFromToken(claims);

                UserDetails userDetails = createUserDetails(claims);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                SecurityContext context = SecurityContextHolder.getContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                populateUserLoggingContext(userDetails);

                log.info("사용자 인증 성공: email = {}", email);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlacklisted(String token) {
        long startedAt = System.nanoTime();
        try {
            boolean blacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
            authRedisMetricsRecorder.recordBlacklistLookup(
                    blacklisted ? "hit" : "miss",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            );
            return blacklisted;
        } catch (RuntimeException exception) {
            authRedisMetricsRecorder.recordBlacklistLookup(
                    "error",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            );
            authRedisMetricsRecorder.incrementBlacklistFallback("redis_error");
            log.warn("Redis 블랙리스트 조회에 실패해 fail-open 정책으로 인증을 계속 진행합니다.", exception);
            return false;
        }
    }

    private UserDetails createUserDetails(Claims claims) {
        String email = jwtUtil.getEmailFromToken(claims);
        Long userId = jwtUtil.getUserIdFromToken(claims);
        UserRole role = jwtUtil.getRoleFromToken(claims);

        if (userId != null && role != null) {
            return new UserDetailsImpl(User.authenticatedPrincipal(userId, email, role));
        }

        return userDetailsService.loadUserByUsername(email);
    }

    private void populateUserLoggingContext(UserDetails userDetails) {
        if (userDetails instanceof UserDetailsImpl userDetailsImpl) {
            MDC.put(LoggingMdcKeys.USER_ID, String.valueOf(userDetailsImpl.getUser().getId()));
        }
    }
}
