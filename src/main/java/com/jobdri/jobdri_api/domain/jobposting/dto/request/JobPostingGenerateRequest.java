package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobPostingGenerateRequest(
        @NotBlank(message = "회사명은 필수입니다.")
        String companyName,
        CompanySize companySize,

        @NotNull(message = "소분류 ID는 필수입니다.")
        Long detailClassificationId,

        String hiringSummary,
        String techStack,
        String mainResponsibilities,
        String requirements,
        String preferredQualifications,
        String tone,
        String jobTitleHint,
        String postingNameHint
) {
    public JobPostingGenerateRequest(
            String companyName,
            CompanySize companySize,
            Long detailClassificationId,
            String hiringSummary,
            String techStack,
            String mainResponsibilities,
            String requirements,
            String preferredQualifications,
            String tone,
            String jobTitleHint
    ) {
        this(
                companyName,
                companySize,
                detailClassificationId,
                hiringSummary,
                techStack,
                mainResponsibilities,
                requirements,
                preferredQualifications,
                tone,
                jobTitleHint,
                null
        );
    }
}
