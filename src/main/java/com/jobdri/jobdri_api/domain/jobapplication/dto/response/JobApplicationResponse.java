package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobApplicationResponse {
    private Long jobApplicationId;
    private Long sourceJobPostingId;
    private Long mockApplyId;
    private String companyName;
    private String postingName;
    private String jobTitle;
    private CompanySize companySize;
    private Long detailClassificationId;
    private String detailClassificationName;
    private String task;
    private String requirement;
    private String preferred;
    private List<String> requiredSkills;
    private LocalDateTime deadlineAt;
    private JobApplicationStage stage;
    private int stageOrder;
    private String currentLabel;
    private LocalDateTime currentAt;
    private String memo;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobApplicationResponse from(JobApplication application) {
        return JobApplicationResponse.builder()
                .jobApplicationId(application.getId())
                .sourceJobPostingId(application.getSourceJobPosting() == null ? null : application.getSourceJobPosting().getId())
                .mockApplyId(application.getMockApply() == null ? null : application.getMockApply().getId())
                .companyName(application.getCompanyName())
                .postingName(application.getPostingName())
                .jobTitle(application.getJobTitle())
                .companySize(application.getCompanySize())
                .detailClassificationId(application.getDetailClassification() == null ? null : application.getDetailClassification().getId())
                .detailClassificationName(application.getDetailClassification() == null ? null : application.getDetailClassification().getDetailName())
                .task(application.getTask())
                .requirement(application.getRequirement())
                .preferred(application.getPreferred())
                .requiredSkills(List.copyOf(application.getRequiredSkills()))
                .deadlineAt(application.getDeadlineAt())
                .stage(application.getStage())
                .stageOrder(application.getStageOrder())
                .currentLabel(application.getCurrentLabel())
                .currentAt(application.getCurrentAt())
                .memo(application.getMemo())
                .archivedAt(application.getArchivedAt())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}
