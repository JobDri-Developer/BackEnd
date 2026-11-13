package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import java.time.LocalDate;

public record JobApplicationIncompleteTaskResponse(
        Long taskId,
        Long jobApplicationId,
        String postingName,
        String companyName,
        String title,
        LocalDate dueDate,
        Long completedChecklistCount,
        Long totalChecklistCount
) {
}
