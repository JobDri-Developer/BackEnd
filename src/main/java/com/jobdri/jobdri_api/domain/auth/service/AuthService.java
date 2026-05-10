package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.dto.request.LoginRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.LogoutRequest;
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
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "RefreshToken:";
    private static final String BLACKLIST_PREFIX = "Blacklist:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
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
        String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId());
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

        String storedRefreshToken = redisTemplate.opsForValue().get(getRefreshTokenKey(accessUserId));
        if (storedRefreshToken == null || !storedRefreshToken.equals(request.refreshToken())) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰 정보가 일치하지 않습니다.");
        }

        User user = userRepository.findById(accessUserId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId());
        String newRefreshToken = jwtUtil.createRefreshToken(user.getEmail());

        saveRefreshToken(user.getId(), newRefreshToken);

        return ReissueTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String accessToken = request.accessToken();
        String refreshToken = request.refreshToken();

        if (!jwtUtil.validateToken(accessToken)) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "유효하지 않은 액세스 토큰입니다.");
        }

        Claims claims = jwtUtil.getClaimsFromToken(accessToken);
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

}
