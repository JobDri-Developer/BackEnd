package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JobPostingImageUploadPresignRequest(
        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,

        @NotBlank(message = "Content-Type은 필수입니다.")
        String contentType
) {
}
