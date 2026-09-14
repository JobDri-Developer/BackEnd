package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobApplicationMetricRequest(
        @NotNull(message = "정량 스펙 유형은 필수입니다.")
        JobApplicationMetricType type,

        @NotBlank(message = "정량 스펙 이름은 필수입니다.")
        @Size(max = 100, message = "정량 스펙 이름은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @NotBlank(message = "정량 스펙 값은 필수입니다.")
        @Size(max = 200, message = "정량 스펙 값은 최대 200자까지 입력할 수 있습니다.")
        String value
) {
}
