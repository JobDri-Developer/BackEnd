package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingIngestInputValidatorTest {

    @Test
    @DisplayName("공백만 입력하고 이미지가 없으면 실패한다")
    void validateRejectsBlankRawTextWithoutImage() {
        assertInvalidParameter(() -> JobPostingIngestInputValidator.validate("          ", List.of()));
    }

    @Test
    @DisplayName("이미지 없이 공백 제외 9자 입력이면 실패한다")
    void validateRejectsNineNonWhitespaceCharactersWithoutImage() {
        assertInvalidParameter(() -> JobPostingIngestInputValidator.validate("123 456 789", List.of()));
    }

    @Test
    @DisplayName("이미지 없이 공백 제외 10자 입력이면 통과한다")
    void validateAcceptsTenNonWhitespaceCharactersWithoutImage() {
        assertThatCode(() -> JobPostingIngestInputValidator.validate("123 456 7890", List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rawText는 10000자까지 허용한다")
    void validateAcceptsMaxLengthRawText() {
        assertThatCode(() -> JobPostingIngestInputValidator.validate("가".repeat(10_000), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rawText가 10000자를 초과하면 실패한다")
    void validateRejectsRawTextOverMaxLength() {
        assertInvalidParameter(() -> JobPostingIngestInputValidator.validate("가".repeat(10_001), List.of()));
    }

    @Test
    @DisplayName("이미지가 있으면 rawText가 10자 미만이어도 허용한다")
    void validateAcceptsShortRawTextWithImage() {
        assertThatCode(() -> JobPostingIngestInputValidator.validate(
                "짧음",
                List.of("job-postings/tmp/1/posting.png")
        ))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rawText와 이미지가 모두 없으면 실패한다")
    void validateRejectsMissingRawTextAndImage() {
        assertInvalidParameter(() -> JobPostingIngestInputValidator.validate(null, null));
    }

    private void assertInvalidParameter(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
