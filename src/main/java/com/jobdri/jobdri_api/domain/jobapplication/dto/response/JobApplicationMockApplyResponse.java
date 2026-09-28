package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;

public record JobApplicationMockApplyResponse(
        Long jobApplicationId,
        Long jobPostingId,
        Long mockApplyId,
        MockApplyStatus status,
        boolean created
) {
    public static JobApplicationMockApplyResponse of(Long jobApplicationId, MockApply mockApply, boolean created) {
        return new JobApplicationMockApplyResponse(
                jobApplicationId,
                mockApply.getJobPosting().getId(),
                mockApply.getId(),
                mockApply.getStatus(),
                created
        );
    }
}
