package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;

import java.util.List;

public record QuestionAnswerResponse(
        Long mockApplyId,
        MockApplyStatus status,
        int sequence,
        List<QuestionResponse> questions
) {
}
