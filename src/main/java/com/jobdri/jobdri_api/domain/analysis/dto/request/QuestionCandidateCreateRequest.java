package com.jobdri.jobdri_api.domain.analysis.dto.request;

import com.jobdri.jobdri_api.domain.analysis.service.question.QuestionDomainSupport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record QuestionCandidateCreateRequest(
        @NotBlank(message = "문항 내용은 필수입니다.")
        String content,

        @Positive(message = "글자수 제한은 1 이상이어야 합니다.")
        @Max(value = QuestionDomainSupport.MAX_CHAR_LIMIT, message = "글자수 제한은 최대 5000자까지 설정할 수 있습니다.")
        Integer charLimit
) {
}
