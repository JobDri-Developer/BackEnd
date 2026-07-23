package com.jobdri.jobdri_api.domain.analysis.dto.llm;

public record CandidateRecheckResponse(
        RecheckDecision decision,
        String candidateId,
        String status,
        String reason,
        String improvement,
        Integer problemClarity,
        Integer jobRelevance,
        Integer evidenceGap,
        Integer improvementUsefulness,
        Integer fabricationConfidence
) {
    public enum RecheckDecision {
        NO_CORRECTION_NEEDED,
        KEEP_BEST_CANDIDATE
    }
}
