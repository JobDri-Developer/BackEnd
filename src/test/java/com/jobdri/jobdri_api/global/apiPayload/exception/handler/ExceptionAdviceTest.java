package com.jobdri.jobdri_api.global.apiPayload.exception.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionAdviceTest {

    @Test
    @DisplayName("민감한 검증 실패 입력값은 키워드별로 마스킹한다")
    void maskAllSensitiveKeywords() {
        assertThat(ExceptionAdvice.formatRejectedValue("password", "raw-password"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("loginRequest.password", "raw-password"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("refreshToken", "raw-token"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("clientSecret", "raw-secret"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("authorizationHeader", "raw-authorization"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("credentialKey", "raw-credential"))
                .isEqualTo("****");
    }

    @Test
    @DisplayName("민감한 검증 실패 입력값은 대소문자와 무관하게 마스킹한다")
    void maskCaseInsensitiveFields() {
        assertThat(ExceptionAdvice.formatRejectedValue("PASSWORD", "raw-password"))
                .isEqualTo("****");
        assertThat(ExceptionAdvice.formatRejectedValue("RefreshToken", "raw-token"))
                .isEqualTo("****");
    }

    @Test
    @DisplayName("민감하지 않은 검증 실패 입력값은 기존처럼 노출한다")
    void keepNonSensitiveRejectedValue() {
        assertThat(ExceptionAdvice.formatRejectedValue("email", "invalid-email"))
                .isEqualTo("invalid-email");
    }

    @Test
    @DisplayName("검증 실패 입력값 포맷팅 시 null 입력을 처리한다")
    void handleNullInputs() {
        assertThat(ExceptionAdvice.formatRejectedValue(null, "raw-value"))
                .isEqualTo("raw-value");
        assertThat(ExceptionAdvice.formatRejectedValue("email", null))
                .isEqualTo("null");
        assertThat(ExceptionAdvice.formatRejectedValue("password", null))
                .isEqualTo("****");
    }
}
