package com.jobdri.jobdri_api.domain.analysis.dto.response;

public record QuestionCandidateResponse(
        String content,
        int charLimit,
        boolean selected
) {
}
