package com.jobdri.jobdri_api.domain.analysis.dto.response;

public record QuestionCandidateResponse(
        Long questionId,
        String content,
        int charLimit,
        boolean selected
) {
}
