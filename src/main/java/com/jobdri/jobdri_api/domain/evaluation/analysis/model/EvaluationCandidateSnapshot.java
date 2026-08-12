package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record EvaluationCandidateSnapshot(
        List<JsonNode> strengthCandidates,
        List<JsonNode> analysisCandidates,
        List<EvaluationMissingKeywordCandidate> missingKeywordCandidates
) {
}
