package com.jobdri.jobdri_api.domain.analysis.dto.external.llm;

import java.util.List;

public record AnalysisLlmResponse(
        Integer jobFit,
        Integer impact,
        Integer completeness,
        String feedback,
        List<HighlightItem> keyStrengths,
        List<HighlightItem> keyWeaknesses,
        List<MissingKeywordItem> missingKeywords,
        List<QuestionAnalysisItem> questionAnalyses
) {
    public AnalysisLlmResponse(
            Integer jobFit,
            Integer impact,
            Integer completeness,
            String feedback,
            List<MissingKeywordItem> missingKeywords,
            List<QuestionAnalysisItem> questionAnalyses
    ) {
        this(jobFit, impact, completeness, feedback, List.of(), List.of(), missingKeywords, questionAnalyses);
    }

    public AnalysisLlmResponse(
            Integer jobFit,
            Integer impact,
            Integer completeness,
            String feedback,
            List<QuestionAnalysisItem> questionAnalyses
    ) {
        this(jobFit, impact, completeness, feedback, List.of(), List.of(), List.of(), questionAnalyses);
    }

    public record HighlightItem(
            String title,
            String quote
    ) {
    }

    public record MissingKeywordItem(
            String keyword,
            String source
    ) {
    }

    public record QuestionAnalysisItem(
            Long questionId,
            String sentence,
            String status,
            String reason,
            String improvement
    ) {
    }
}
