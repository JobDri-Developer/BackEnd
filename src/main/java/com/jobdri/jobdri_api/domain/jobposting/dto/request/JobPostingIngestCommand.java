package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobPostingIngestCommand {

    private Long userId;
    private String rawText;
    private String sourceUrl;
    private byte[] imageBytes;
    private String imageContentType;
}
