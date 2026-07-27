package com.jobdri.jobdri_api.global.jwt;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import com.jobdri.jobdri_api.global.security.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, redisTemplate);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("role claim이 있으면 DB 조회 없이 인증 객체를 구성한다")
    void authenticatesWithoutDatabaseLookupWhenRoleClaimExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtUtil.AUTHORIZATION_HEADER, JwtUtil.BEARER_PREFIX + "access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        Claims claims = mock(Claims.class);

        when(redisTemplate.hasKey("Blacklist:access-token")).thenReturn(false);
        when(jwtUtil.substringToken(JwtUtil.BEARER_PREFIX + "access-token")).thenReturn("access-token");
        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.getClaimsFromToken("access-token")).thenReturn(claims);
        when(jwtUtil.getEmailFromToken(claims)).thenReturn("user@example.com");
        when(jwtUtil.getUserIdFromToken(claims)).thenReturn(42L);
        when(jwtUtil.getRoleFromToken(claims)).thenReturn(UserRole.USER);

        filter.doFilter(request, response, filterChain);

        verify(userDetailsService, never()).loadUserByUsername("user@example.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UserDetailsImpl.class);
        UserDetailsImpl principal = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getUser().getId()).isEqualTo(42L);
        assertThat(principal.getUser().getEmail()).isEqualTo("user@example.com");
        assertThat(principal.getUser().getRole()).isEqualTo(UserRole.USER);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("구형 access token은 기존처럼 DB 조회로 호환한다")
    void fallsBackToDatabaseLookupWhenRoleClaimMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtUtil.AUTHORIZATION_HEADER, JwtUtil.BEARER_PREFIX + "legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        Claims claims = mock(Claims.class);
        UserDetailsImpl userDetails = new UserDetailsImpl(User.authenticatedPrincipal(7L, "legacy@example.com", UserRole.USER));

        when(redisTemplate.hasKey("Blacklist:legacy-token")).thenReturn(false);
        when(jwtUtil.substringToken(JwtUtil.BEARER_PREFIX + "legacy-token")).thenReturn("legacy-token");
        when(jwtUtil.validateToken("legacy-token")).thenReturn(true);
        when(jwtUtil.getClaimsFromToken("legacy-token")).thenReturn(claims);
        when(jwtUtil.getEmailFromToken(claims)).thenReturn("legacy@example.com");
        when(jwtUtil.getUserIdFromToken(claims)).thenReturn(7L);
        when(jwtUtil.getRoleFromToken(claims)).thenReturn(null);
        when(userDetailsService.loadUserByUsername("legacy@example.com")).thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);

        verify(userDetailsService).loadUserByUsername("legacy@example.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }
}
