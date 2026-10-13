package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import jakarta.validation.constraints.Size;

/**
 * 이름이 빈 행과 값이 빈 어학·기타 행은 저장 시 제외된다. 자격증과 수상은 값이 비어도 저장된다.
 */
public record JobApplicationMetricRequest(
        JobApplicationMetricType type,

        @Size(max = 100, message = "정량 스펙 이름은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @Size(max = 200, message = "정량 스펙 값은 최대 200자까지 입력할 수 있습니다.")
        String value
) {
}
