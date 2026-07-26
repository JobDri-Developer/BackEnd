package com.jobdri.jobdri_api.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmationRequest(
        @NotBlank(message = "토큰은 필수입니다.")
        String token,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$",
                message = "비밀번호는 영문, 숫자를 포함한 8~20자여야 합니다."
        )
        String newPassword
) {
}
