package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;

public record QuestionAnalysisResponse(
        Long questionAnalysisId,
        String sentence,
        String reason,
        String improvement,
        int start,
        int end
) {
    public static QuestionAnalysisResponse from(QuestionAnalysis questionAnalysis) {
        return new QuestionAnalysisResponse(
                questionAnalysis.getId(),
                questionAnalysis.getSentence(),
                questionAnalysis.getReason(),
                questionAnalysis.getImprovement(),
                questionAnalysis.getStart(),
                questionAnalysis.getEnd()
        );
    }
}
