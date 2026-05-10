package com.jobdri.jobdri_api.domain.auth.controller;

import com.jobdri.jobdri_api.domain.auth.dto.request.EmailSendRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.EmailVerificationRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.LoginRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.LogoutRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.ReissueTokenRequest;
import com.jobdri.jobdri_api.domain.auth.dto.request.SignupRequest;
import com.jobdri.jobdri_api.domain.auth.dto.response.LoginResponse;
import com.jobdri.jobdri_api.domain.auth.dto.response.ReissueTokenResponse;
import com.jobdri.jobdri_api.domain.auth.service.AuthService;
import com.jobdri.jobdri_api.domain.auth.service.EmailService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth", description = "인증 및 이메일 인증 API")
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @Operation(summary = "이메일 인증번호 발송", description = "회원가입을 위해 이메일로 인증번호를 발송합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 발송 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"인증번호가 성공적으로 발송되었습니다.\",\"result\":null,\"error\":null}")
                    )
            )
    })
    @PostMapping("/email/send-code")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(@Valid @RequestBody EmailSendRequest request) {
        emailService.sendVerificationCode(request.email());
        return ResponseEntity.ok(ApiResponse.onSuccess("인증번호가 성공적으로 발송되었습니다."));
    }

    @Operation(summary = "이메일 인증번호 검증", description = "회원가입 전, 이메일로 발송된 인증번호를 검증합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이메일 인증 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"이메일 인증에 성공하였습니다.\",\"result\":null,\"error\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증번호 불일치 (에러 코드 AUTH_4002)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"AUTH_4002\",\"message\":\"이메일 인증번호가 유효하지 않습니다.\",\"result\":null,\"error\":\"인증번호가 일치하지 않습니다.\"}")
                    )
            )
    })
    @PostMapping("/email/verify-code")
    public ResponseEntity<ApiResponse<Void>> verifyEmailCode(@Valid @RequestBody EmailVerificationRequest request) {
        emailService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.onSuccess("이메일 인증에 성공하였습니다."));
    }

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

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.onSuccess("로그아웃이 완료되었습니다.");
    }
}
