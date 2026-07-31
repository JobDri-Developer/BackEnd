package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingGenerateResponse(
        String postingName,
        String companyName,
        String jobTitle,
        String task,
        String requirements,
        String preferredQualifications,
        String summary
) {
    public JobPostingGenerateResponse {
        requirements = JobPostingLineBreakFormatter.appendLineBreakAfterLastLine(requirements);
        preferredQualifications = JobPostingLineBreakFormatter.appendLineBreakAfterLastLine(preferredQualifications);
    }
}
