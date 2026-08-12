package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.CandidateReviewResponse;

public record EvaluationGeneratedResult(
        AnalysisLlmResponse response,
        AnalysisCandidateResponse rawCandidateResponse,
        AnalysisCandidateResponse sanitizedCandidateResponse,
        CandidateReviewResponse candidateReviewResponse,
        long candidateCallLatencyMs,
        long finalCallLatencyMs
) {
}
