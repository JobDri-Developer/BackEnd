package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import org.springframework.web.multipart.MultipartFile;

public record JobPostingIngestMultipartRequest(String rawText, String sourceUrl, MultipartFile image) {

}
