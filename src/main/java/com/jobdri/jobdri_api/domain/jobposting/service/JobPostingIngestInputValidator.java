package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;

import java.util.List;

public final class JobPostingIngestInputValidator {

    private static final int MIN_RAW_TEXT_NON_WHITESPACE_LENGTH = 10;
    private static final int MAX_RAW_TEXT_LENGTH = 10_000;

    private JobPostingIngestInputValidator() {
    }

    public static void validate(String rawText, List<String> imageObjectKeys) {
        boolean hasRawText = hasText(rawText);
        boolean hasImage = imageObjectKeys != null && !imageObjectKeys.isEmpty();

        if (!hasRawText && !hasImage) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "공고 텍스트를 입력하거나 채용 공고 이미지를 첨부해주세요."
            );
        }
        if (hasRawText && rawText.length() > MAX_RAW_TEXT_LENGTH) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "공고 내용은 최대 10,000자까지 입력할 수 있습니다."
            );
        }
        if (!hasImage && nonWhitespaceLength(rawText) < MIN_RAW_TEXT_NON_WHITESPACE_LENGTH) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "정확한 분석을 위해 공고 내용을 최소 10자 이상 입력해주세요."
            );
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nonWhitespaceLength(String value) {
        if (value == null) {
            return 0;
        }
        return (int) value.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count();
    }
}
