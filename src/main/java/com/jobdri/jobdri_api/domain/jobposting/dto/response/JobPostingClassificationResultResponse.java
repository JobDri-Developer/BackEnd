package com.jobdri.jobdri_api.domain.jobposting.dto.response;

public record JobPostingClassificationResultResponse(Long detailClassificationId, String detailClassificationName,
                                                     String middleClassificationName, String bigClassificationName,
                                                     String reason, double confidence) {

}
