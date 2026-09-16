package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobApplicationChecklistItemRequest(
        @NotBlank(message = "체크리스트 내용은 필수입니다.")
        @Size(max = 200, message = "체크리스트 내용은 최대 200자까지 입력할 수 있습니다.")
        String content,
        boolean completed
) {
}
