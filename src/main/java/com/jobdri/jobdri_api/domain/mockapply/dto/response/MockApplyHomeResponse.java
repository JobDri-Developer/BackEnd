package com.jobdri.jobdri_api.domain.mockapply.dto.response;

import java.util.List;

public record MockApplyHomeResponse(
        List<MockApplyHomeItemResponse> inProgress,
        List<MockApplyHomeItemResponse> completed
) {
}
