package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JobPostingExtractRequest(
        @NotBlank(message = "채용 공고 원문은 필수입니다.")
        String rawText
) {
}
