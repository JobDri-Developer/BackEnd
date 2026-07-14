package com.jobdri.jobdri_api.domain.analysis.evaluation;

record EvaluationAnalysisResult(
        String caseId,
        String jobCategoryMiddle,
        String jobCategorySmall,
        Integer aiScore,
        Integer aiJobFit,
        Integer aiImpact,
        Integer aiCompleteness,
        String aiFeedback,
        String aiMissingKeywordsJson,
        String aiQuestionAnalysesJson,
        String rawLlmResponseJson,
        String errorMessage,
        String createdAt
) {
    static EvaluationAnalysisResult failed(EvaluationAnalysisCase evaluationCase, String errorMessage, String createdAt) {
        return new EvaluationAnalysisResult(
                evaluationCase.caseId(),
                evaluationCase.jobCategoryMiddle(),
                evaluationCase.jobCategorySmall(),
                null,
                null,
                null,
                null,
                "",
                "[]",
                "[]",
                "",
                errorMessage,
                createdAt
        );
    }
}
