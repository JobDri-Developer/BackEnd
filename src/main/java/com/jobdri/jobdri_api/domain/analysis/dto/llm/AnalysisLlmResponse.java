package com.jobdri.jobdri_api.domain.analysis.dto.llm;

import java.util.List;

public record AnalysisLlmResponse(
        Integer score,
        Integer jobFit,
        Integer impact,
        Integer completeness,
        String feedback,
        List<QuestionAnalysisItem> questionAnalyses
) {
    public record QuestionAnalysisItem(
            Long questionId,
            String sentence,
            String reason,
            String improvement
    ) {
    }
}
