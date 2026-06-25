package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JobPostingExtensionIngestRequest(
        String sourceUrl,
        String sourceSite,

        @NotBlank(message = "크롤링한 공고 내용은 필수입니다.")
        String rawText
) {
}
