package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingGenerateResponse(String companyName, String jobTitle, String task, String requirements,
                                         String preferredQualifications, String summary) {

}
