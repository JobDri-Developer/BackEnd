package com.jobdri.jobdri_api.domain.analysis.evaluation;

record EvaluationQuestionAnalysisResult(
        Long questionId,
        String sentence,
        String status,
        String reason,
        String improvement,
        int startIndex,
        int endIndex
) {
}
