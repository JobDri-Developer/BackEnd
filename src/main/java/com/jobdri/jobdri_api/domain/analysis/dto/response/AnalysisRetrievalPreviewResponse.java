package com.jobdri.jobdri_api.domain.analysis.dto.response;

import java.util.List;

public record AnalysisRetrievalPreviewResponse(
        Long mockApplyId,
        JobPostingSnapshot jobPosting,
        List<QuestionSnapshot> questions,
        List<JobPostingReference> similarJobPostings,
        List<QuestionReference> similarQuestions
) {
    public record JobPostingSnapshot(
            Long jobPostingId,
            String companyName,
            String detailClassificationName,
            String task,
            String requirement,
            String preferred
    ) {
    }

    public record QuestionSnapshot(
            Long questionId,
            String content,
            String answer
    ) {
    }

    public record JobPostingReference(
            Long corpusId,
            String companyName,
            String roleName,
            String responsibilities,
            String requirements,
            String preferred,
            double distance
    ) {
    }

    public record QuestionReference(
            Long corpusId,
            String companyName,
            String roleName,
            String questionType,
            Integer charLimit,
            String questionText,
            double distance
    ) {
    }
}
