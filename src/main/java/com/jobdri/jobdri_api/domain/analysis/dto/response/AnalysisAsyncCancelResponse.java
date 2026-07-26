package com.jobdri.jobdri_api.domain.analysis.dto.response;

public record AnalysisAsyncCancelResponse(
        String taskId,
        String status,
        String message
) {
}
