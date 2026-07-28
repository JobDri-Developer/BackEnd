package com.jobdri.jobdri_api.domain.analysis.service.sanitization;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;

public record MissingKeywordSanitizationDecision(
        int candidateIndex,
        AnalysisCandidateResponse.MissingKeywordCandidate originalCandidate,
        String normalizedKeyword,
        boolean accepted,
        MissingKeywordRejectionReason rejectionReason,
        boolean answerExactMatch,
        boolean answerNormalizedMatch,
        boolean jdRequirementMatched,
        Integer duplicateOfCandidateIndex
) {
}
