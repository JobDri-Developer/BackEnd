package com.jobdri.jobdri_api.global.jwt;

import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET =
            "am9iZHJpLWxvY2FsLXNlY3JldC1rZXktZm9yLWRldmVsb3BtZW50LTIwMjYtam9iZHJp";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenTime", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenTime", 1_209_600_000L);
        jwtUtil.init();
    }

    @Test
    @DisplayName("access token은 access 타입으로 발급되고 refresh 검증을 통과하지 못한다")
    void accessTokenIsOnlyAccessType() {
        String accessToken = jwtUtil.createAccessToken("user@example.com", 1L, UserRole.USER);
        Claims claims = jwtUtil.getClaimsFromToken(accessToken);

        assertThat(claims.get(JwtUtil.TOKEN_TYPE_CLAIM, String.class)).isEqualTo(JwtUtil.ACCESS_TOKEN_TYPE);
        assertThat(jwtUtil.isAccessToken(claims)).isTrue();
        assertThat(jwtUtil.isRefreshToken(claims)).isFalse();
        assertThat(jwtUtil.validateRefreshToken(accessToken)).isFalse();
    }

    @Test
    @DisplayName("refresh token은 refresh 타입으로 발급되고 access 토큰으로 인정되지 않는다")
    void refreshTokenIsOnlyRefreshType() {
        String refreshToken = jwtUtil.createRefreshToken("user@example.com");
        Claims claims = jwtUtil.getClaimsFromToken(refreshToken);

        assertThat(claims.get(JwtUtil.TOKEN_TYPE_CLAIM, String.class)).isEqualTo(JwtUtil.REFRESH_TOKEN_TYPE);
        assertThat(jwtUtil.isAccessToken(claims)).isFalse();
        assertThat(jwtUtil.isRefreshToken(claims)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("tokenType이 없는 구형 토큰은 userId 유무로 access와 refresh를 구분한다")
    void classifiesLegacyTokensByUserIdClaim() {
        String legacyAccessToken = legacyToken(1L);
        String legacyRefreshToken = legacyToken(null);

        Claims accessClaims = jwtUtil.getClaimsFromToken(legacyAccessToken);
        Claims refreshClaims = jwtUtil.getClaimsFromToken(legacyRefreshToken);

        assertThat(jwtUtil.isAccessToken(accessClaims)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(legacyAccessToken)).isFalse();
        assertThat(jwtUtil.isAccessToken(refreshClaims)).isFalse();
        assertThat(jwtUtil.validateRefreshToken(legacyRefreshToken)).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 tokenType은 access와 refresh 어느 쪽으로도 인정하지 않는다")
    void rejectsUnknownTokenType() {
        Claims claims = Jwts.claims().add(JwtUtil.TOKEN_TYPE_CLAIM, "password-reset").add("userId", 1L).build();

        assertThat(jwtUtil.isAccessToken(claims)).isFalse();
        assertThat(jwtUtil.isRefreshToken(claims)).isFalse();
    }

    @Test
    @DisplayName("서명이 맞지 않는 토큰은 refresh 검증을 통과하지 못한다")
    void rejectsRefreshTokenWithInvalidSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "other-secret-key-for-testing-jobdri-jwt-util-2026".getBytes(StandardCharsets.UTF_8)
        );
        String forged = Jwts.builder()
                .subject("user@example.com")
                .claim(JwtUtil.TOKEN_TYPE_CLAIM, JwtUtil.REFRESH_TOKEN_TYPE)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThat(jwtUtil.validateRefreshToken(forged)).isFalse();
    }

    private String legacyToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        var builder = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key);
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.compact();
    }
}
