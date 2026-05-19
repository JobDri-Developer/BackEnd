package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;

import java.util.List;

public record QuestionSelectionResponse(
        Long mockApplyId,
        MockApplyStatus status,
        List<QuestionResponse> questions
) {
}
