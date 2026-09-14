package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;

import java.time.LocalDateTime;

public record JobApplicationArchiveItemResponse(
        Long jobApplicationId,
        String companyName,
        String postingName,
        String jobTitle,
        LocalDateTime deadlineAt,
        String currentLabel,
        LocalDateTime currentAt,
        JobApplicationStage stage,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
