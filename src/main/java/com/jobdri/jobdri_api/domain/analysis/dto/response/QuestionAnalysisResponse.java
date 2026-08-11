package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.type.QuestionAnalysisStatus;

public record QuestionAnalysisResponse(
        Long questionAnalysisId,
        String sentence,
        String status,
        String reason,
        String improvement,
        int start,
        int end
) {
    public static QuestionAnalysisResponse from(QuestionAnalysis questionAnalysis) {
        return new QuestionAnalysisResponse(
                questionAnalysis.getId(),
                questionAnalysis.getSentence(),
                statusValue(questionAnalysis.getStatus()),
                questionAnalysis.getReason(),
                questionAnalysis.getImprovement(),
                questionAnalysis.getStart(),
                questionAnalysis.getEnd()
        );
    }

    private static String statusValue(QuestionAnalysisStatus status) {
        if (status == null) {
            return QuestionAnalysisStatus.MENTIONED.name().toLowerCase();
        }
        return status.name().toLowerCase();
    }
}
