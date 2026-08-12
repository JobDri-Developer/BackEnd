package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationQuestionAnalysis(
        Long questionId,
        String sentence,
        String status,
        String reason,
        String improvement
) {
}
