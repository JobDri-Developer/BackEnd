package com.jobdri.jobdri_api.domain.analysis.dto.response;

public record AnalysisProgressStepResponse(
        String code,
        String label,
        String status
) {
}
