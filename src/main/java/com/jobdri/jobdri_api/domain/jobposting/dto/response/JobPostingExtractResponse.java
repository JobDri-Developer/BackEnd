package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingExtractResponse {

    private String companyName;
    private String jobTitle;
    private String task;
    private String requirements;
    private String preferredQualifications;
    private String rawText;
    private double confidence;
}
