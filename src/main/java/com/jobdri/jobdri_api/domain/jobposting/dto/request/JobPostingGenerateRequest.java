package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobPostingGenerateRequest(
        @NotBlank(message = "회사명은 필수입니다.")
        String companyName,

        @NotNull(message = "회사 규모는 필수입니다.")
        CompanySize companySize,

        @NotBlank(message = "직무명은 필수입니다.")
        String jobTitle,

        String hiringSummary,
        String techStack,
        String mainResponsibilities,
        String requirements,
        String preferredQualifications,
        String tone
) {
}
