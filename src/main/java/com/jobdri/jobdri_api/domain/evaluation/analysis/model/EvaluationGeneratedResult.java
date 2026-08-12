package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationGeneratedResult(
        EvaluationLlmSnapshot responseSnapshot,
        String rawLlmResponseJson,
        String rawCandidateResponseJson,
        String sanitizedCandidateResponseJson,
        EvaluationCandidateSnapshot sanitizedCandidateSnapshot,
        String candidateReviewResponseJson,
        EvaluationCandidateReviewSnapshot candidateReviewSnapshot,
        long candidateCallLatencyMs,
        long finalCallLatencyMs
) {
}
