package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationMissingKeyword(
        String keyword,
        EvaluationMissingKeywordSource source
) {
}
