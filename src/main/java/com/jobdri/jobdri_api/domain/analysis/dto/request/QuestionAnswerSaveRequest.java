package com.jobdri.jobdri_api.domain.analysis.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuestionAnswerSaveRequest(
        @Valid
        @NotEmpty(message = "저장할 답변은 1개 이상이어야 합니다.")
        List<AnswerItem> answers
) {
    public record AnswerItem(
            @NotNull(message = "문항 ID는 필수입니다.")
            Long questionId,

            @NotBlank(message = "문항 내용은 필수입니다.")
            String content,

            @NotNull(message = "답변 내용은 필수입니다.")
            String answer
    ) {
    }
}
