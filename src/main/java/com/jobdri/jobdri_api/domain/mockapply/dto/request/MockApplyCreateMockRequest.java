package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import jakarta.validation.constraints.NotNull;

public record MockApplyCreateMockRequest(
        @NotNull(message = "회사 ID는 필수입니다.")
        Long companyId,

        @NotNull(message = "중분류 ID는 필수입니다.")
        Long middleClassificationId,

        @NotNull(message = "소분류 ID는 필수입니다.")
        Long detailClassificationId
) {
    public JobPostingMockGenerateRequest toJobPostingMockGenerateRequest() {
        return new JobPostingMockGenerateRequest(companyId, middleClassificationId, detailClassificationId);
    }
}
