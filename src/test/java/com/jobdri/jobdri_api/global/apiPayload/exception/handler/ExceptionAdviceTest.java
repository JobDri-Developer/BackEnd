package com.jobdri.jobdri_api.global.apiPayload.exception.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @Test
    @DisplayName("path variable 타입 변환 실패 메시지는 파라미터 이름과 입력값을 포함한다")
    void formatTypeMismatchMessage() throws NoSuchMethodException {
        ExceptionAdvice advice = new ExceptionAdvice();
        MethodParameter parameter = new MethodParameter(
                SampleController.class.getDeclaredMethod("endpoint", Long.class),
                0
        );

        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "NaN",
                Long.class,
                "mockApplyId",
                parameter,
                new TypeMismatchException("NaN", Long.class)
        );

        var response = advice.handleMethodArgumentTypeMismatchException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("파라미터 형식이 잘못되었습니다.");
        assertThat(response.getBody().getError())
                .isEqualTo("파라미터 'mockApplyId'의 형식이 잘못되었습니다. (입력값: NaN)");
    }

    @Test
    @DisplayName("중복/유니크 충돌은 기존처럼 400으로 처리한다")
    void handleDuplicateDataIntegrityViolationAsBadRequest() {
        ExceptionAdvice advice = new ExceptionAdvice();
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("uk_worker_task_results_task_id");

        var response = advice.handleDataIntegrityViolationException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("파라미터 형식이 잘못되었습니다.");
        assertThat(response.getBody().getError()).isEqualTo("이미 처리되었거나 중복된 요청입니다.");
    }

    @Test
    @DisplayName("길이 초과 같은 저장 무결성 오류는 500으로 구분한다")
    void handleNonDuplicateDataIntegrityViolationAsServerError() {
        ExceptionAdvice advice = new ExceptionAdvice();
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: value too long for type character varying(255)")
        );

        var response = advice.handleDataIntegrityViolationException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("서버 내부 오류입니다.");
        assertThat(response.getBody().getError()).isEqualTo("데이터 저장 중 무결성 오류가 발생했습니다.");
    }

    private static class SampleController {
        @SuppressWarnings("unused")
        void endpoint(Long mockApplyId) {
        }
    }
}
