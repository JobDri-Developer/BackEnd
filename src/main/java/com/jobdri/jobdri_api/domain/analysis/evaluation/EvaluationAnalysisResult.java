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
        String rawCandidateResponseJson,
        String sanitizedCandidateResponseJson,
        Integer candidateAnalysisCount,
        Integer candidateStrengthCount,
        Integer candidateMissingKeywordCount,
        Long candidateCallLatencyMs,
        Long finalCallLatencyMs,
        String failureStage,
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
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                failureStage(errorMessage),
                errorMessage,
                createdAt
        );
    }

    private static String failureStage(String errorMessage) {
        if (errorMessage == null) {
            return "";
        }
        if (errorMessage.contains("candidate")) {
            return "candidate_call_failed";
        }
        if (errorMessage.contains("final")) {
            return "final_call_failed";
        }
        return "";
    }
}
