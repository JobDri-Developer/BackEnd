package com.jobdri.jobdri_api.domain.analysis.service.sanitization;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;

import java.util.List;

public record MissingKeywordSanitizationResult(
        List<AnalysisCandidateResponse.MissingKeywordCandidate> acceptedCandidates,
        List<MissingKeywordSanitizationDecision> decisions
) {
}
