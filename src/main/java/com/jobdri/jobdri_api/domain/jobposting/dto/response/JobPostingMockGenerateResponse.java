package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingMockGenerateResponse(
        String companyName,
        String jobTitle,
        String task,
        String requirement,
        String preferred,
        String summary
) {
}
