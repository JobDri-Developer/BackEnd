package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;

import java.util.Locale;
import java.util.Set;

final class JobPostingIngestQualityValidator {

    private static final double MIN_EXTRACT_CONFIDENCE = 0.3;
    private static final int MIN_SHORT_FIELD_LENGTH = 2;
    private static final int MIN_DESCRIPTION_LENGTH = 5;
    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "string",
            "null",
            "n/a",
            "na",
            "none",
            "unknown",
            "-",
            "없음",
            "해당 없음",
            "미정",
            "미지정",
            "미분류",
            "미분류 회사"
    );

    private JobPostingIngestQualityValidator() {
    }

    static void validateExtracted(JobPostingExtractResponse extracted) {
        if (extracted == null) {
            throwInvalidJobPosting();
        }
        if (Double.isNaN(extracted.confidence())
                || Double.isInfinite(extracted.confidence())
                || extracted.confidence() < MIN_EXTRACT_CONFIDENCE
                || extracted.confidence() > 1.0) {
            throwInvalidJobPosting();
        }
        validateField(extracted.companyName(), MIN_SHORT_FIELD_LENGTH);
        validateField(extracted.jobTitle(), MIN_SHORT_FIELD_LENGTH);
        validateField(extracted.task(), MIN_DESCRIPTION_LENGTH);
        validateField(extracted.requirements(), MIN_DESCRIPTION_LENGTH);
    }

    static void validateGenerated(JobPostingGenerateResponse generated) {
        if (generated == null) {
            throwInvalidJobPosting();
        }
        validateField(generated.companyName(), MIN_SHORT_FIELD_LENGTH);
        validateField(generated.jobTitle(), MIN_SHORT_FIELD_LENGTH);
        validateField(generated.task(), MIN_DESCRIPTION_LENGTH);
        validateField(generated.requirements(), MIN_DESCRIPTION_LENGTH);
    }

    private static void validateField(String value, int minLength) {
        if (value == null) {
            throwInvalidJobPosting();
        }

        String normalized = value.trim();
        if (normalized.length() < minLength || isPlaceholder(normalized)) {
            throwInvalidJobPosting();
        }
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return PLACEHOLDER_VALUES.contains(normalized) || normalized.matches("string\\d*");
    }

    private static void throwInvalidJobPosting() {
        throw new GeneralException(
                GeneralErrorCode.INVALID_PARAMETER,
                "채용 공고로 인식할 수 없는 입력입니다. 공고 내용을 확인해주세요."
        );
    }
}
