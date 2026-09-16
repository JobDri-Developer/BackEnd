package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record JobApplicationPositionRequest(
        @NotNull JobApplicationStage targetStage,
        @NotNull @Min(0) Integer targetIndex
) {}
