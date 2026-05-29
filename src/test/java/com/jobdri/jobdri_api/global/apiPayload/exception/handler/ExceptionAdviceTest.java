package com.jobdri.jobdri_api.global.apiPayload.exception.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionAdviceTest {

    @Test
    @DisplayName("민감한 검증 실패 입력값은 마스킹한다")
    void maskSensitiveRejectedValue() {
        assertThat(ExceptionAdvice.formatRejectedValue("password", "raw-password"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("loginRequest.password", "raw-password"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("refreshToken", "raw-token"))
                .isEqualTo("****");
    }

    @Test
    @DisplayName("민감하지 않은 검증 실패 입력값은 기존처럼 노출한다")
    void keepNonSensitiveRejectedValue() {
        assertThat(ExceptionAdvice.formatRejectedValue("email", "invalid-email"))
                .isEqualTo("invalid-email");
    }
}
