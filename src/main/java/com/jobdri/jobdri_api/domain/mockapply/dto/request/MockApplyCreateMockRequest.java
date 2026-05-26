package com.jobdri.jobdri_api.domain.mockapply.dto.request;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MockApplyCreateMockRequest(
        @NotNull(message = "회사 ID는 필수입니다.")
        Long companyId,

        @NotNull(message = "중분류 ID는 필수입니다.")
        Long middleClassificationId,

        @NotNull(message = "소분류 ID는 필수입니다.")
        Long detailClassificationId,

        @Positive(message = "지원 순번은 1 이상이어야 합니다.")
        Integer sequence
) {
    public MockApplyCreateMockRequest(Long companyId, Long middleClassificationId, Long detailClassificationId) {
        this(companyId, middleClassificationId, detailClassificationId, null);
    }

    public JobPostingMockGenerateRequest toJobPostingMockGenerateRequest() {
        return new JobPostingMockGenerateRequest(companyId, middleClassificationId, detailClassificationId);
    }
}
