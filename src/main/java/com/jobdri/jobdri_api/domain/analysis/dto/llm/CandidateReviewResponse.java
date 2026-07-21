package com.jobdri.jobdri_api.domain.analysis.dto.llm;

import java.util.List;

public record CandidateReviewResponse(
        List<CandidateDecision> decisions,
        List<FinalStrengthCandidate> strengths,
        List<FinalMissingKeywordCandidate> missingKeywords,
        Integer jobFit,
        Integer impact,
        Integer completeness,
        String feedback
) {
    public record CandidateDecision(
            String candidateId,
            Boolean accepted,
            String rejectionCode,
            String status,
            String reason,
            String improvement
    ) {
    }

    public record FinalStrengthCandidate(
            String title,
            String quote,
            String relatedSource
    ) {
    }

    public record FinalMissingKeywordCandidate(
            String keyword,
            String source
    ) {
    }
}
