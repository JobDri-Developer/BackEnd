package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobPostingResponse {

    private Long jobPostingId;
    private Long companyId;
    private String companyName;
    private String companySize;
    private Long detailClassificationId;
    private String detailClassificationName;
    private String task;
    private String requirement;
    private String preferred;

    public static JobPostingResponse from(JobPosting jobPosting) {
        return JobPostingResponse.builder()
                .jobPostingId(jobPosting.getId())
                .companyId(jobPosting.getCompany().getId())
                .companyName(jobPosting.getCompany().getName())
                .companySize(jobPosting.getCompany().getSize().name())
                .detailClassificationId(jobPosting.getDetailClassification().getId())
                .detailClassificationName(jobPosting.getDetailClassification().getDetailName())
                .task(jobPosting.getTask())
                .requirement(jobPosting.getRequirement())
                .preferred(jobPosting.getPreferred())
                .build();
    }
}
