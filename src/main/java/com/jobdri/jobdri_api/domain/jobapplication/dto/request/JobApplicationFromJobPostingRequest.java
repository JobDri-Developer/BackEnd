package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import jakarta.validation.constraints.NotNull;

public record JobApplicationFromJobPostingRequest(
        @NotNull(message = "공고 ID는 필수입니다.")
        Long jobPostingId,
        JobApplicationStage initialStage
) {
}
