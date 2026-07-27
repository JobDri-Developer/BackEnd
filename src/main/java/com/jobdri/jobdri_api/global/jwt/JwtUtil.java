package com.jobdri.jobdri_api.global.jwt;

import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;

@Slf4j(topic = "JwtUtil")
@Component
public class JwtUtil {

    @Value("${jwt.expiration.access-token}")
    private long accessTokenTime;

    @Value("${jwt.expiration.refresh-token}")
    private long refreshTokenTime;

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret.key}")
    private String secretKey;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(String email, Long userId, UserRole role) {
        return createToken(email, userId, role, accessTokenTime);
    }

    public String createRefreshToken(String email) {
        return createToken(email, null, null, refreshTokenTime);
    }

    public long getRefreshTokenTime() {
        return refreshTokenTime;
    }

    public long getRemainingTime(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }

    private String createToken(String email, Long userId, UserRole role, long expireTime) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireTime);

        JwtBuilder builder = Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(key);

        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (role != null) {
            builder.claim("role", role.name());
        }

        return builder.compact();
    }

    public String substringToken(String tokenValue) {
        if (tokenValue != null && tokenValue.startsWith(BEARER_PREFIX)) {
            return tokenValue.substring(BEARER_PREFIX.length());
        }

        throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "유효하지 않은 토큰입니다.");
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature, 유효하지 않은 JWT 서명 입니다.");
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token, 만료된 JWT token 입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        }
        return false;
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public String getEmailFromToken(Claims claims) {
        return claims.getSubject();
    }

    public Long getUserIdFromToken(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public UserRole getRoleFromToken(Claims claims) {
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "유효하지 않은 권한 정보입니다.");
        }
    }

    public Claims getClaimsFromExpiredToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (Exception e) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "유효하지 않은 토큰입니다.");
        }
    }
}
