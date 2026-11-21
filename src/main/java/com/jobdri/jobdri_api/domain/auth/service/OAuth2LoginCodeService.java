package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 로그인 성공 후 토큰을 URL에 노출하지 않도록 일회용 로그인 코드를 발급하고 교환한다.
 * 프론트는 redirect URL의 code를 받아 {@code POST /api/auth/oauth2/token}으로 토큰을 받는다.
 */
@Service
@RequiredArgsConstructor
public class OAuth2LoginCodeService {

    static final String LOGIN_CODE_PREFIX = "OAuth2LoginCode:";
    static final long LOGIN_CODE_TTL_SECONDS = 60L;
    private static final String TOKEN_DELIMITER = "\n";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public String issue(LoginResponse tokens) {
        String code = createCode();
        redisTemplate.opsForValue().set(
                getLoginCodeKey(code),
                tokens.accessToken() + TOKEN_DELIMITER + tokens.refreshToken(),
                LOGIN_CODE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return code;
    }

    /**
     * 코드를 원자적으로 꺼내면서 삭제하므로 같은 코드는 한 번만 교환할 수 있다.
     */
    public LoginResponse exchange(String code) {
        if (!StringUtils.hasText(code)) {
            throw invalidCode();
        }
        String stored = redisTemplate.opsForValue().getAndDelete(getLoginCodeKey(code));
        if (stored == null) {
            throw invalidCode();
        }
        String[] tokens = stored.split(TOKEN_DELIMITER, 2);
        if (tokens.length != 2 || !StringUtils.hasText(tokens[0]) || !StringUtils.hasText(tokens[1])) {
            throw invalidCode();
        }
        return LoginResponse.builder()
                .accessToken(tokens[0])
                .refreshToken(tokens[1])
                .build();
    }

    private GeneralException invalidCode() {
        return new GeneralException(GeneralErrorCode.INVALID_TOKEN, "로그인 코드가 만료되었거나 이미 사용되었습니다.");
    }

    private String getLoginCodeKey(String code) {
        return LOGIN_CODE_PREFIX + sha256(code);
    }

    private String createCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
