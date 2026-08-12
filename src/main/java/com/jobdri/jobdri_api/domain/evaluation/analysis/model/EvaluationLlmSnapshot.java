package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

import java.util.List;

public record EvaluationLlmSnapshot(
        Integer jobFit,
        Integer impact,
        Integer completeness,
        String feedback,
        List<String> keyStrengthQuotes,
        List<EvaluationMissingKeyword> missingKeywords,
        List<EvaluationQuestionAnalysis> questionAnalyses
) {
}
