package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobApplicationIngestRequest(
        @NotBlank(message = "멱등 키는 필수입니다.")
        @Size(max = 100, message = "멱등 키는 최대 100자까지 입력할 수 있습니다.")
        String idempotencyKey,
        String rawText,
        String imageObjectKey,
        List<String> imageObjectKeys
) {
    @AssertTrue(message = "rawText 또는 이미지 objectKey 중 하나는 반드시 포함되어야 합니다.")
    public boolean hasInput() {
        return hasText(rawText) || hasText(imageObjectKey)
                || imageObjectKeys != null && imageObjectKeys.stream().anyMatch(this::hasText);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
