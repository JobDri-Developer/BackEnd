package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import java.util.List;

public record JobPostingExtractResponse(String postingName, String companyName, String jobTitle, String task,
                                        String requirements, String preferredQualifications, String rawText,
                                        double confidence, List<String> requiredSkills, String deadlineAt) {

    public JobPostingExtractResponse(
            String postingName,
            String companyName,
            String jobTitle,
            String task,
            String requirements,
            String preferredQualifications,
            String rawText,
            double confidence
    ) {
        this(postingName, companyName, jobTitle, task, requirements, preferredQualifications, rawText, confidence,
                List.of(), "");
    }

    public JobPostingExtractResponse(
            String companyName,
            String jobTitle,
            String task,
            String requirements,
            String preferredQualifications,
            String rawText,
            double confidence
    ) {
        this("", companyName, jobTitle, task, requirements, preferredQualifications, rawText, confidence,
                List.of(), "");
    }
}
