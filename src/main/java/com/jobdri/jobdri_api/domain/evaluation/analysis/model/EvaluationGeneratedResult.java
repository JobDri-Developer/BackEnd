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
        long finalCallLatencyMs,
        Integer candidateInputTokens,
        Integer candidateOutputTokens,
        Integer finalInputTokens,
        Integer finalOutputTokens
) {
    public EvaluationGeneratedResult(
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
        this(
                responseSnapshot, rawLlmResponseJson, rawCandidateResponseJson,
                sanitizedCandidateResponseJson, sanitizedCandidateSnapshot,
                candidateReviewResponseJson, candidateReviewSnapshot,
                candidateCallLatencyMs, finalCallLatencyMs,
                null, null, null, null
        );
    }
}
