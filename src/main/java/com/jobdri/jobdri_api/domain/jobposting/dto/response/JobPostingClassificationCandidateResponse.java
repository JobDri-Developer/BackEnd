package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobPostingClassificationCandidateResponse {

    private Long detailClassificationId;
    private String detailClassificationName;
    private String middleClassificationName;
    private String bigClassificationName;
    private double score;
}
