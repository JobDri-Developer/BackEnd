package com.jobdri.jobdri_api.domain.auth.controller;

import com.jobdri.jobdri_api.domain.auth.dto.request.LoginRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.ReissueTokenRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.SignupRequest;
import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.auth.dto.response.ReissueTokenResponse;
import com.jobdri.jobdri_api.domain.auth.service.AuthService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.onSuccess("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.onSuccess("로그인에 성공했습니다.", authService.login(request));
    }

    @PostMapping("/reissue")
    public ApiResponse<ReissueTokenResponse> reissue(@Valid @RequestBody ReissueTokenRequest request) {
        return ApiResponse.onSuccess("토큰이 재발급되었습니다.", authService.reissueToken(request));
    }
}
