package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobPostingIngestCommand {

    private String rawText;
    private String sourceUrl;
    private byte[] imageBytes;
    private String imageContentType;
    private CompanySize companySize;
    private String tone;
    private Integer candidateLimit;
}
