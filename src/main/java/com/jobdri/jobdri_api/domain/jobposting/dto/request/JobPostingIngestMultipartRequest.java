package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class JobPostingIngestMultipartRequest {

    private String rawText;
    private String sourceUrl;
    private MultipartFile image;
    private CompanySize companySize;
    private String tone;
    private Integer candidateLimit;
}
