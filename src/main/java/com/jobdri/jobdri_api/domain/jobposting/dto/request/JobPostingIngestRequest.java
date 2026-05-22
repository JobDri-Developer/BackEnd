package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.AssertTrue;

public record JobPostingIngestRequest(
        String rawText,
        String imageObjectKey
) {

    @AssertTrue(message = "rawText 또는 imageObjectKey 중 하나는 반드시 포함되어야 합니다.")
    public boolean hasInput() {
        return hasText(rawText) || hasText(imageObjectKey);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
