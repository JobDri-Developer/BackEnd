package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

import java.util.List;

public record EvaluationLlmSnapshot(
        List<String> keyStrengthQuotes,
        List<EvaluationMissingKeyword> missingKeywords,
        List<EvaluationQuestionAnalysis> questionAnalyses
) {
}
