package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import java.time.LocalDateTime;
import java.util.List;

public record JobApplicationCardResponse(
        Long jobApplicationId, String companyName, String postingName, String jobTitle,
        List<String> requiredSkills, long essayQuestionCount, LocalDateTime deadlineAt,
        String currentLabel, LocalDateTime currentAt, JobApplicationStage stage,
        int stageOrder, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static JobApplicationCardResponse from(JobApplication card, long essayQuestionCount) {
        return new JobApplicationCardResponse(
                card.getId(), card.getCompanyName(), card.getPostingName(), card.getJobTitle(),
                List.copyOf(card.getRequiredSkills()), essayQuestionCount, card.getDeadlineAt(),
                card.getCurrentLabel(), card.getCurrentAt(), card.getStage(), card.getStageOrder(),
                card.getCreatedAt(), card.getUpdatedAt());
    }
}
