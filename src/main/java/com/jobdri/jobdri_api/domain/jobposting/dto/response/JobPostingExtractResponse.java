package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingExtractResponse(String companyName, String jobTitle, String task, String requirements,
                                        String preferredQualifications, String rawText, double confidence) {

}
