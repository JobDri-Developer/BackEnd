package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import java.util.List;

public record JobPostingMockQuestionResponse(
        List<String> recommendedQuestions
) {
}
