package com.jobdri.jobdri_api.domain.analysis.dto.llm;

import java.util.List;

public record AnalysisCandidateResponse(
        List<StrengthCandidate> strengthCandidates,
        List<AnalysisCandidate> analysisCandidates,
        List<MissingKeywordCandidate> missingKeywordCandidates
) {
    public record StrengthCandidate(
            Long questionId,
            String quote,
            String relatedSource,
            String relatedRequirement,
            String reasonBasis
    ) {
    }

    public record AnalysisCandidate(
            String candidateId,
            Long questionId,
            String sentence,
            String contextBefore,
            String contextAfter,
            String sentenceType,
            String relatedSource,
            String relatedRequirement,
            String status,
            String issueType,
            String reasonBasis
    ) {
    }

    public record MissingKeywordCandidate(
            String keyword,
            String source,
            String relatedRequirement
    ) {
    }
}
