package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

public record JobApplicationEssaySourceResponse(
        Long jobApplicationId, String companyName, String postingName, String jobTitle, long essayQuestionCount
) {}
