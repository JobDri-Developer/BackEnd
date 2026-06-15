package com.jobdri.jobdri_api.domain.corpus.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CorpusImportRequest(
        @NotBlank(message = "엑셀 파일 경로는 필수입니다.")
        String filePath
) {
}
