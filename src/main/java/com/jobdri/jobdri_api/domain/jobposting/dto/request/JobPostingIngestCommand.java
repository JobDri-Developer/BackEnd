package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JobPostingIngestCommand {

    private Long userId;
    private String rawText;
    private String imageObjectKey;
    private List<String> imageObjectKeys;
}
