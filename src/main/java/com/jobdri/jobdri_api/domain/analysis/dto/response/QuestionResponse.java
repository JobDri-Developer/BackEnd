package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;

public record QuestionResponse(
        Long questionId,
        String content,
        int charLimit,
        String answer,
        boolean custom
) {
    public static QuestionResponse from(Question question) {
        return from(question, false);
    }

    public static QuestionResponse from(Question question, boolean custom) {
        return new QuestionResponse(
                question.getId(),
                question.getContent(),
                question.getLimit(),
                question.getAnswer(),
                custom
        );
    }
}
