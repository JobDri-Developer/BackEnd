package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestValidationErrorResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestValidationErrorResponse.InvalidField;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class JobPostingIngestQualityValidator {

    private static final int MIN_SHORT_FIELD_LENGTH = 2;
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
            throwInvalidJobPostingWithFields(List.of(
                    invalidField("companyName", "회사명"),
                    invalidField("jobTitle", "직무")
            ));
        }
        if (Double.isNaN(extracted.confidence())
                || Double.isInfinite(extracted.confidence())
                || extracted.confidence() < 0.0
                || extracted.confidence() > 1.0) {
            throwInvalidJobPosting();
        }
        validateRequiredFields(List.of(
                field("companyName", "회사명", extracted.companyName()),
                field("jobTitle", "직무", extracted.jobTitle())
        ));
    }

    static void validateGenerated(JobPostingGenerateResponse generated) {
        if (generated == null) {
            throwInvalidJobPostingWithFields(List.of(
                    invalidField("postingName", "공고명"),
                    invalidField("companyName", "회사명"),
                    invalidField("jobTitle", "직무")
            ));
        }
        validateRequiredFields(List.of(
                field("postingName", "공고명", generated.postingName()),
                field("companyName", "회사명", generated.companyName()),
                field("jobTitle", "직무", generated.jobTitle())
        ));
    }

    private static void validateRequiredFields(List<FieldCandidate> candidates) {
        List<InvalidField> invalidFields = new ArrayList<>();
        for (FieldCandidate candidate : candidates) {
            if (isInvalidRequiredField(candidate.value())) {
                invalidFields.add(invalidField(candidate.field(), candidate.label()));
            }
        }

        if (!invalidFields.isEmpty()) {
            throwInvalidJobPostingWithFields(invalidFields);
        }
    }

    private static boolean isInvalidRequiredField(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim();
        return normalized.length() < MIN_SHORT_FIELD_LENGTH || isPlaceholder(normalized);
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

    private static void throwInvalidJobPostingWithFields(List<InvalidField> invalidFields) {
        throw new GeneralException(
                GeneralErrorCode.INVALID_PARAMETER,
                "채용 공고 필수 정보를 인식하지 못했습니다. 공고 내용을 확인해주세요.",
                new JobPostingIngestValidationErrorResponse(
                        "INVALID_JOB_POSTING_FIELDS",
                        "공고명, 회사명, 직무 정보를 확인해주세요.",
                        invalidFields
                )
        );
    }

    private static FieldCandidate field(String field, String label, String value) {
        return new FieldCandidate(field, label, value);
    }

    private static InvalidField invalidField(String field, String label) {
        return new InvalidField(field, label, label + "을(를) 인식하지 못했습니다.");
    }

    private record FieldCandidate(
            String field,
            String label,
            String value
    ) {
    }
}
