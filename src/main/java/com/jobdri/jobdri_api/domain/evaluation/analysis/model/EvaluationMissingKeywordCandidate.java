package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationMissingKeywordCandidate(
        String keyword,
        EvaluationMissingKeywordSource source,
        String relatedRequirement
) {
}
