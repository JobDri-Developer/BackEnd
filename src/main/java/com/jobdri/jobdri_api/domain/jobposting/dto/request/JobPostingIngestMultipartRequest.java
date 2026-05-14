package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import org.springframework.web.multipart.MultipartFile;

public record JobPostingIngestMultipartRequest(String rawText, String sourceUrl, MultipartFile image,
                                               CompanySize companySize, Integer candidateLimit) {

}
