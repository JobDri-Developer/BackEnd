package com.jobdri.jobdri_api.domain.analysis.dto.request;

import com.jobdri.jobdri_api.domain.analysis.service.question.QuestionDomainSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionSelectionSaveRequest(
        @Valid
        @NotEmpty(message = "선택 문항은 1개 이상이어야 합니다.")
        @Size(max = 5, message = "문항은 최대 5개까지 선택할 수 있습니다.")
        List<QuestionSelectionItem> questions
) {
    public record QuestionSelectionItem(
            @NotBlank(message = "문항 내용은 필수입니다.")
            String content,

            @Positive(message = "글자수 제한은 1 이상이어야 합니다.")
            @Max(value = QuestionDomainSupport.MAX_CHAR_LIMIT, message = "글자수 제한은 최대 5000자까지 설정할 수 있습니다.")
            Integer charLimit,

            Boolean custom
    ) {
    }
}
