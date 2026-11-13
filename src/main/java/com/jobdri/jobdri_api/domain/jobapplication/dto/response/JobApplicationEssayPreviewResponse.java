package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

public record JobApplicationEssayPreviewResponse(
        Long essayId, String question, String answer, int displayOrder
) {}
