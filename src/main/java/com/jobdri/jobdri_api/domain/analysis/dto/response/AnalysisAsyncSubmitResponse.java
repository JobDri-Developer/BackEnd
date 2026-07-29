package com.jobdri.jobdri_api.domain.analysis.dto.response;

public record AnalysisAsyncSubmitResponse(
        String taskId,
        String status,
        String message,
        boolean cached,
        boolean resultAvailable
) {
}
