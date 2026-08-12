package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

import java.util.List;

public record EvaluationCandidateSnapshot(
        List<EvaluationMissingKeywordCandidate> missingKeywordCandidates
) {
}
