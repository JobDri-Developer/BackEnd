package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.dto.request.LoginRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.LogoutRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.PasswordResetConfirmationRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.PasswordResetEmailRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.ReissueTokenRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.SignupRequest;
import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.auth.dto.response.ReissueTokenResponse;
import com.jobdri.jobdri_api.domain.user.entity.SocialType;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.jwt.JwtUtil;
import com.jobdri.jobdri_api.global.logging.LoggingMdcKeys;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "RefreshToken:";
    private static final String BLACKLIST_PREFIX = "Blacklist:";
    private static final String REISSUE_LOCK_PREFIX = "ReissueLock:";
    private static final String PASSWORD_RESET_TOKEN_PREFIX = "PasswordResetToken:";
    private static final String PASSWORD_RESET_COOLDOWN_PREFIX = "PasswordResetCooldown:";
    private static final String PASSWORD_RESET_IP_COOLDOWN_PREFIX = "PasswordResetIpCooldown:";
    private static final long REISSUE_LOCK_TIMEOUT_SECONDS = 3L;
    private static final long PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES = 30L;
    private static final long PASSWORD_RESET_COOLDOWN_MINUTES = 5L;
    private static final long PASSWORD_RESET_IP_COOLDOWN_SECONDS = 60L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final AsyncEmailSender asyncEmailSender;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void signup(SignupRequest request) {
        emailService.checkEmailVerified(request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_LOGINID, "이미 존재하는 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.signup(request.name(), request.email(), encodedPassword);

        try {
            userRepository.save(user);
            emailService.deleteVerifiedEmailFlag(request.email());
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_LOGINID, "이미 존재하는 이메일입니다.");
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일과 비밀번호를 확인해주세요."));

        if (user.getSocialType() != SocialType.LOCAL) {
            throw new GeneralException(
                    GeneralErrorCode.SOCIAL_LOGIN_REQUIRED,
                    "Google 계정으로 연동된 이메일입니다. 소셜 로그인을 이용해주세요"
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일과 비밀번호를 확인해주세요.");
        }

        return issueTokens(user);
    }

    public LoginResponse issueTokens(User user) {
        String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId(), user.getRole());
        String refreshTokenValue = jwtUtil.createRefreshToken(user.getEmail());

        saveRefreshToken(user.getId(), refreshTokenValue);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    @Transactional
    public ReissueTokenResponse reissueToken(ReissueTokenRequest request) {
        Claims accessClaims = jwtUtil.getClaimsFromExpiredToken(request.accessToken());
        Long accessUserId = accessClaims.get("userId", Long.class);

        if (accessUserId == null || !jwtUtil.validateToken(request.refreshToken())) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
        }

        String lockKey = getReissueLockKey(accessUserId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                request.refreshToken(),
                REISSUE_LOCK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(locked)) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "토큰 재발급 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
            );
        }

        try {
            String storedRefreshToken = redisTemplate.opsForValue().get(getRefreshTokenKey(accessUserId));
            if (storedRefreshToken == null || !storedRefreshToken.equals(request.refreshToken())) {
                throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰 정보가 일치하지 않습니다.");
            }

            User user = userRepository.findById(accessUserId)
                    .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

            String newAccessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId(), user.getRole());
            String newRefreshToken = jwtUtil.createRefreshToken(user.getEmail());

            saveRefreshToken(user.getId(), newRefreshToken);

            return ReissueTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String accessToken = request.accessToken();
        String refreshToken = request.refreshToken();

        Claims claims = extractLogoutClaims(accessToken);
        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
        }

        String storedRefreshToken = redisTemplate.opsForValue().get(getRefreshTokenKey(userId));
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰 정보가 일치하지 않습니다.");
        }

        redisTemplate.delete(getRefreshTokenKey(userId));

        long remainingTime = jwtUtil.getRemainingTime(accessToken);
        if (remainingTime > 0) {
            redisTemplate.opsForValue().set(
                    getBlacklistKey(accessToken),
                    "logout",
                    remainingTime,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public void sendPasswordResetEmail(PasswordResetEmailRequest request) {
        if (isPasswordResetIpRateLimited()) {
            return;
        }

        userRepository.findByEmail(request.email())
                .filter(user -> user.getSocialType() == SocialType.LOCAL)
                .ifPresent(user -> {
                    if (!acquirePasswordResetCooldown(user.getId())) {
                        return;
                    }
                    String token = createPasswordResetToken();
                    redisTemplate.opsForValue().set(
                            getPasswordResetTokenKey(token),
                            String.valueOf(user.getId()),
                            PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES,
                            TimeUnit.MINUTES
                    );
                    asyncEmailSender.sendPasswordResetMail(user.getEmail(), token);
                });
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmationRequest request) {
        String tokenKey = getPasswordResetTokenKey(request.token());
        String userIdValue = redisTemplate.opsForValue().get(tokenKey);
        if (userIdValue == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "비밀번호 재설정 토큰이 유효하지 않습니다.");
        }

        Long userId = parsePasswordResetUserId(userIdValue);
        String lockKey = getReissueLockKey(userId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "password-reset",
                REISSUE_LOCK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(locked)) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "비밀번호 재설정 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
            );
        }

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

            if (user.getSocialType() != SocialType.LOCAL) {
                throw new GeneralException(
                        GeneralErrorCode.SOCIAL_LOGIN_REQUIRED,
                        "Google 계정은 비밀번호를 재설정할 수 없습니다. 소셜 로그인을 이용해주세요."
                );
            }

            user.updatePassword(passwordEncoder.encode(request.newPassword()));
            redisTemplate.delete(tokenKey);
            redisTemplate.delete(getRefreshTokenKey(user.getId()));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private Claims extractLogoutClaims(String accessToken) {
        try {
            return jwtUtil.getClaimsFromToken(accessToken);
        } catch (GeneralException exception) {
            return jwtUtil.getClaimsFromExpiredToken(accessToken);
        }
    }

    private void saveRefreshToken(Long userId, String refreshTokenValue) {
        redisTemplate.opsForValue().set(
                getRefreshTokenKey(userId),
                refreshTokenValue,
                jwtUtil.getRefreshTokenTime(),
                TimeUnit.MILLISECONDS
        );
    }

    private String getRefreshTokenKey(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }

    private String getBlacklistKey(String accessToken) {
        return BLACKLIST_PREFIX + accessToken;
    }

    private String getReissueLockKey(Long userId) {
        return REISSUE_LOCK_PREFIX + userId;
    }

    private boolean isPasswordResetIpRateLimited() {
        String clientIp = MDC.get(LoggingMdcKeys.CLIENT_IP);
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                getPasswordResetIpCooldownKey(clientIp),
                "1",
                PASSWORD_RESET_IP_COOLDOWN_SECONDS,
                TimeUnit.SECONDS
        );
        return !Boolean.TRUE.equals(acquired);
    }

    private boolean acquirePasswordResetCooldown(Long userId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                getPasswordResetCooldownKey(userId),
                "1",
                PASSWORD_RESET_COOLDOWN_MINUTES,
                TimeUnit.MINUTES
        );
        return Boolean.TRUE.equals(acquired);
    }

    private String getPasswordResetTokenKey(String token) {
        return PASSWORD_RESET_TOKEN_PREFIX + sha256(token);
    }

    private String getPasswordResetCooldownKey(Long userId) {
        return PASSWORD_RESET_COOLDOWN_PREFIX + userId;
    }

    private String getPasswordResetIpCooldownKey(String clientIp) {
        return PASSWORD_RESET_IP_COOLDOWN_PREFIX + sha256(clientIp);
    }

    private String createPasswordResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "비밀번호 재설정 토큰 처리에 실패했습니다.");
        }
    }

    private Long parsePasswordResetUserId(String userIdValue) {
        try {
            return Long.parseLong(userIdValue);
        } catch (NumberFormatException exception) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "비밀번호 재설정 토큰이 유효하지 않습니다.");
        }
    }

}
