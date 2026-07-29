package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingSimilarityResult(
        Long jobPostingId,
        String postingName,
        String companyName,
        String jobTitle,
        double similarityScore
) {
}
