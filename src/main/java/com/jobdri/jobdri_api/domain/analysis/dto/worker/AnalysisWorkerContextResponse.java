package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import java.util.List;

public record AnalysisWorkerContextResponse(
        Long userId,
        Long mockApplyId,
        String companyName,
        String jobTitle,
        String task,
        String requirements,
        String preferredQualifications,
        String bigClassificationName,
        String middleClassificationName,
        String detailClassificationName,
        List<AnalysisWorkerQuestionItem> questions
) {
    public record AnalysisWorkerQuestionItem(
            Long questionId,
            String question,
            String answer,
            int charLimit
    ) {
    }
}
