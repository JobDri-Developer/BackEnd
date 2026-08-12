package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationCandidateReviewDecision(
        Boolean accepted,
        String rejectionCode
) {
}
