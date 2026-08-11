package com.jobdri.jobdri_api.domain.analysis.dto.external.llm;

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
            RejectionCode rejectionCode,
            String status,
            String reason,
            String improvement
    ) {
    }

    public enum RejectionCode {
        ALREADY_SPECIFIC,
        CONTEXT_PROVIDES_EVIDENCE,
        WRONG_SENTENCE_TYPE_CRITERIA,
        PREFERENCE_ONLY,
        NOT_JOB_RELEVANT,
        DUPLICATE_ISSUE,
        UNSUPPORTED_JUDGMENT,
        NOT_ACTIONABLE,
        INVALID_SOURCE,
        NONE
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
