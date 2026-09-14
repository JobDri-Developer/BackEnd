package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobApplicationEssayRequest(
        @NotBlank(message = "자기소개서 질문은 필수입니다.")
        @Size(max = 1000, message = "자기소개서 질문은 최대 1000자까지 입력할 수 있습니다.")
        String question,

        @Size(max = 10000, message = "자기소개서 답변은 최대 10000자까지 입력할 수 있습니다.")
        String answer
) {
}
