package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;

import java.util.List;

public record AnalysisQuestionResponse(
        Long questionId,
        String questionContent,
        String answer,
        List<QuestionAnalysisResponse> analyses
) {
    public static AnalysisQuestionResponse of(
            Question question,
            List<QuestionAnalysisResponse> analyses
    ) {
        return new AnalysisQuestionResponse(
                question.getId(),
                question.getContent(),
                question.getAnswer(),
                analyses
        );
    }
}
