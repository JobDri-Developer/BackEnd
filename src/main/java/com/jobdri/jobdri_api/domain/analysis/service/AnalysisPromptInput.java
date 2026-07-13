package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;

import java.util.List;

public record AnalysisPromptInput(
        String companyName,
        String jobName,
        String mainTasks,
        String qualifications,
        String preferences,
        List<QuestionAnswer> questions
) {
    public static AnalysisPromptInput from(JobPosting jobPosting, List<Question> questions) {
        return new AnalysisPromptInput(
                jobPosting != null && jobPosting.getCompany() != null ? jobPosting.getCompany().getName() : "",
                jobPosting != null && jobPosting.getDetailClassification() != null
                        ? jobPosting.getDetailClassification().getDetailName()
                        : "",
                jobPosting != null ? jobPosting.getTask() : "",
                jobPosting != null ? jobPosting.getRequirement() : "",
                jobPosting != null ? jobPosting.getPreferred() : "",
                questions == null
                        ? List.of()
                        : questions.stream()
                        .map(question -> new QuestionAnswer(
                                question.getId(),
                                question.getContent(),
                                question.getAnswer()
                        ))
                        .toList()
        );
    }

    public record QuestionAnswer(
            Long questionId,
            String question,
            String answer
    ) {
    }
}
