package com.jobdri.jobdri_api.domain.analysis.evaluation;

import java.util.List;

public record NlgEvaluationResponse(
        String caseId,
        List<QuestionAnalysisEvaluation> questionAnalysisEvaluations,
        Integer noAnalysisAppropriateness,
        Integer strengthsPrecision,
        Integer strengthsCoverage,
        Integer missingKeywordsPrecision,
        Integer missingKeywordsCoverage,
        Integer overallUsefulness,
        List<NlgEvaluationErrorCode> caseErrorCodes,
        String shortRationale
) {
    public record QuestionAnalysisEvaluation(
            Integer analysisIndex,
            String sentence,
            Integer relevance,
            Integer problemValidity,
            Integer sentenceTypeConsistency,
            Integer reasonCorrectness,
            Integer contextAwareness,
            Integer faithfulness,
            Integer tenseConsistency,
            Integer usability,
            Integer nonMeta,
            Integer meaningPreservation,
            List<NlgEvaluationErrorCode> errorCodes
    ) {
    }
}
