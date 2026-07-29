package com.jobdri.jobdri_api.domain.analysis.dto.worker;

public record SimilarJobPostingContext(
        Long jobPostingId,
        String companyName,
        String postingName,
        String jobTitle,
        String task,
        String requirements,
        String preferredQualifications,
        int similarityRank,
        double similarityScore
) {
}
