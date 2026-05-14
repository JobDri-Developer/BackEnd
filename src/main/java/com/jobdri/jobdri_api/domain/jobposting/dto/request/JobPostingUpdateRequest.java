package com.jobdri.jobdri_api.domain.jobposting.dto.request;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobPostingUpdateRequest(
        @NotBlank(message = "회사명은 필수입니다.")
        String companyName,

        CompanySize companySize,

        @NotNull(message = "소분류 ID는 필수입니다.")
        Long detailClassificationId,

        @NotBlank(message = "주요 업무는 필수입니다.")
        String task,

        @NotBlank(message = "자격 요건은 필수입니다.")
        String requirement,

        @NotBlank(message = "우대 사항은 필수입니다.")
        String preferred
) {
}
