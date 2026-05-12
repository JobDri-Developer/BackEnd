package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingClassificationResultResponse {

    private Long detailClassificationId;
    private String detailClassificationName;
    private String middleClassificationName;
    private String bigClassificationName;
    private String reason;
    private double confidence;
}
