package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingProgressStepResponse(
        String code,
        String label,
        String status
) {
}
