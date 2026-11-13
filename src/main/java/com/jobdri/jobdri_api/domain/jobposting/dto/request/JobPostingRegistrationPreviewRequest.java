package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import jakarta.validation.constraints.AssertTrue;

import java.util.List;

public record JobPostingRegistrationPreviewRequest(
        String rawText, String imageObjectKey, List<String> imageObjectKeys, String postingUrl
) {
    /** Keeps the existing text/image contract and requires URL input to be used on its own. */
    @AssertTrue(message = "공고 URL은 텍스트나 이미지와 함께 입력할 수 없으며 미리보기 입력은 필수입니다.")
    public boolean isValidInput() {
        boolean hasExtractInput = new JobPostingExtractRequest(rawText, imageObjectKey, imageObjectKeys).hasInput();
        return postingUrl == null ? hasExtractInput : !hasExtractInput;
    }
}
