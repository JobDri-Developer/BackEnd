package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingResponse {

    private Long jobPostingId;
    private Long userId;
    private JobPostingProfileColor profileColor;
    private String postingName;
    private Long companyId;
    private String companyName;
    private String companySize;
    private String jobTitle;
    private Long detailClassificationId;
    private String detailClassificationName;
    private String task;
    private String requirement;
    private String preferred;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobPostingResponse from(JobPosting jobPosting) {
        return JobPostingResponse.builder()
                .jobPostingId(jobPosting.getId())
                .userId(jobPosting.getUser().getId())
                .profileColor(jobPosting.getProfileColor())
                .postingName(jobPosting.getPostingName())
                .companyId(jobPosting.getCompany().getId())
                .companyName(jobPosting.getCompany().getName())
                .companySize(jobPosting.getCompany().getSize() == null ? null : jobPosting.getCompany().getSize().name())
                .jobTitle(jobPosting.getJobTitle())
                .detailClassificationId(jobPosting.getDetailClassification().getId())
                .detailClassificationName(jobPosting.getDetailClassification().getDetailName())
                .task(jobPosting.getTask())
                .requirement(jobPosting.getRequirement())
                .preferred(jobPosting.getPreferred())
                .createdAt(jobPosting.getCreatedAt())
                .updatedAt(jobPosting.getUpdatedAt())
                .build();
    }
}
