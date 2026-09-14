package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;

import java.time.LocalDateTime;
import java.util.List;

public record JobApplicationArchiveItemResponse(
        Long jobApplicationId,
        String companyName,
        String postingName,
        String jobTitle,
        List<String> requiredSkills,
        LocalDateTime deadlineAt,
        String currentLabel,
        LocalDateTime currentAt,
        JobApplicationStage stage,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobApplicationArchiveItemResponse from(JobApplication application) {
        return new JobApplicationArchiveItemResponse(
                application.getId(),
                application.getCompanyName(),
                application.getPostingName(),
                application.getJobTitle(),
                List.copyOf(application.getRequiredSkills()),
                application.getDeadlineAt(),
                application.getCurrentLabel(),
                application.getCurrentAt(),
                application.getStage(),
                application.getArchivedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
