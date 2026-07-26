package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.AssertTrue;

import java.util.List;

public record JobPostingIngestRequest(
        String rawText,
        String imageObjectKey,
        List<String> imageObjectKeys
) {

    public JobPostingIngestRequest(String rawText, String imageObjectKey) {
        this(rawText, imageObjectKey, null);
    }

    @AssertTrue(message = "rawText 또는 이미지 objectKey 중 하나는 반드시 포함되어야 합니다.")
    public boolean hasInput() {
        return hasText(rawText) || hasText(imageObjectKey) || hasImageObjectKeys();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasImageObjectKeys() {
        return imageObjectKeys != null && imageObjectKeys.stream().anyMatch(this::hasText);
    }
}
