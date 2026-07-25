package com.jobdri.jobdri_api.domain.mockapply.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record MockApplyHomeResponse(
        List<MockApplyHomeItemResponse> inProgress,
        Page<MockApplyHomeItemResponse> completed
) {
}
