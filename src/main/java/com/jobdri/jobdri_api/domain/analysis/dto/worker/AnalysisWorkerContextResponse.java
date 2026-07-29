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
        List<AnalysisWorkerQuestionItem> questions,
        List<SimilarJobPostingContext> similarJobPostings
) {
    public AnalysisWorkerContextResponse(
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
        this(
                userId,
                mockApplyId,
                companyName,
                jobTitle,
                task,
                requirements,
                preferredQualifications,
                bigClassificationName,
                middleClassificationName,
                detailClassificationName,
                questions,
                List.of()
        );
    }

    public AnalysisWorkerContextResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
        similarJobPostings = similarJobPostings == null ? List.of() : List.copyOf(similarJobPostings);
    }

    public record AnalysisWorkerQuestionItem(
            Long questionId,
            String question,
            String answer,
            int charLimit
    ) {
    }
}
