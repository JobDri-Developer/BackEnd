package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingAsyncCancelResponse(
        String taskId,
        String status,
        String message
) {
}
