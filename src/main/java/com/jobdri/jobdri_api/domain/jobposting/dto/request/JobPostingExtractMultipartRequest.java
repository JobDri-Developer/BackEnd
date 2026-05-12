package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class JobPostingExtractMultipartRequest {

    private String rawText;
    private String sourceUrl;
    private MultipartFile image;
}
