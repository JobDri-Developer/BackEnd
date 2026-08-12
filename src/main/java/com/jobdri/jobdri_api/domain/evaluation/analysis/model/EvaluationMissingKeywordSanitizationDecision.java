package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationMissingKeywordSanitizationDecision(
        int candidateIndex,
        EvaluationMissingKeywordCandidate originalCandidate,
        String normalizedKeyword,
        boolean accepted,
        EvaluationMissingKeywordRejectionReason rejectionReason,
        boolean answerExactMatch,
        boolean answerNormalizedMatch,
        boolean jdRequirementMatched,
        Integer duplicateOfCandidateIndex
) {
}
