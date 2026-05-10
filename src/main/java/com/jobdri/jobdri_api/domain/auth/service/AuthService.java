package com.jobdri.jobdri_api.domain.auth.service;

import com.jobdri.jobdri_api.domain.auth.dto.request.LoginRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.ReissueTokenRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.SignupRequest;
import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.auth.dto.response.ReissueTokenResponse;
import com.jobdri.jobdri_api.domain.auth.entity.RefreshToken;
import com.jobdri.jobdri_api.domain.auth.repository.RefreshTokenRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_LOGINID, "이미 존재하는 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.signup(request.name(), request.email(), encodedPassword);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_LOGINID, "이미 존재하는 이메일입니다.");
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = getUserByEmail(request.email());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new GeneralException(GeneralErrorCode.INVALID_LOGIN);
        }

        String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId());
        String refreshTokenValue = jwtUtil.createRefreshToken(user.getEmail());

        upsertRefreshToken(user, refreshTokenValue);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    @Transactional
    public ReissueTokenResponse reissueToken(ReissueTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_TOKEN));

        Claims accessClaims = jwtUtil.getClaimsFromExpiredToken(request.accessToken());
        Long accessUserId = accessClaims.get("userId", Long.class);

        if (accessUserId == null || !storedToken.isOwnedBy(accessUserId)) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰 정보가 일치하지 않습니다.");
        }

        User user = storedToken.getUser();
        String newAccessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId());
        String newRefreshToken = jwtUtil.createRefreshToken(user.getEmail());

        storedToken.rotate(newRefreshToken);

        return ReissueTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private void upsertRefreshToken(User user, String refreshTokenValue) {
        refreshTokenRepository.findByUser_Id(user.getId())
                .ifPresentOrElse(
                        token -> token.rotate(refreshTokenValue),
                        () -> refreshTokenRepository.save(RefreshToken.create(user, refreshTokenValue))
                );
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_LOGIN));
    }
}
