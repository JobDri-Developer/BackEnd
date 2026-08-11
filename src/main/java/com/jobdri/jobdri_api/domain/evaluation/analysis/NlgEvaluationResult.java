package com.jobdri.jobdri_api.domain.evaluation.analysis;

record NlgEvaluationResult(
        String caseId,
        String sourceResultFile,
        Integer analysisCount,
        Double averageRelevance,
        Double averageProblemValidity,
        Double averageSentenceTypeConsistency,
        Double averageReasonCorrectness,
        Double averageContextAwareness,
        Double averageFaithfulness,
        Double averageTenseConsistency,
        Double averageUsability,
        Double averageNonMeta,
        Double averageMeaningPreservation,
        Integer noAnalysisAppropriateness,
        Integer strengthsPrecision,
        Integer strengthsCoverage,
        Integer missingKeywordsPrecision,
        Integer missingKeywordsCoverage,
        Integer overallUsefulness,
        String errorCodes,
        String shortRationale,
        Integer judgeInputTokens,
        Integer judgeOutputTokens,
        Long judgeLatencyMs,
        String failureStage
) {
    static NlgEvaluationResult failed(String caseId, String sourceResultFile, String failureStage) {
        return new NlgEvaluationResult(
                caseId,
                sourceResultFile,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "[]",
                "",
                null,
                null,
                null,
                failureStage
        );
    }
}
