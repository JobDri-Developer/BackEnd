package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;

import java.util.List;

public record AnalysisResponse(
        Long mockApplyId,
        Long analysisId,
        MockApplyStatus status,
        int sequence,
        int score,
        int jobFit,
        int impact,
        int completeness,
        String feedback,
        List<AnalysisQuestionResponse> questions
) {
    public static AnalysisResponse of(
            Analysis analysis,
            MockApplyStatus status,
            int sequence,
            List<AnalysisQuestionResponse> questions
    ) {
        return new AnalysisResponse(
                analysis.getMockApply().getId(),
                analysis.getId(),
                status,
                sequence,
                analysis.getScore(),
                analysis.getJobFit(),
                analysis.getImpact(),
                analysis.getCompleteness(),
                analysis.getFeedback(),
                questions
        );
    }
}
