package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationQuestionAnalysis;

public final class EvaluationQuestionAnalysisMapper {
    private EvaluationQuestionAnalysisMapper() {
    }

    public static EvaluationQuestionAnalysis from(AnalysisLlmResponse.QuestionAnalysisItem item) {
        if (item == null) {
            return null;
        }
        return new EvaluationQuestionAnalysis(
                item.questionId(),
                item.sentence(),
                item.status(),
                item.reason(),
                item.improvement()
        );
    }
}
