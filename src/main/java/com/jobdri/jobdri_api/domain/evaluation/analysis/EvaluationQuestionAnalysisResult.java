package com.jobdri.jobdri_api.domain.evaluation.analysis;

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
