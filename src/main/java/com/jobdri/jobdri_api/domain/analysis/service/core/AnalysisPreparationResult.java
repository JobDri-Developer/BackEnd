package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.internal.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;

import java.util.List;

public record AnalysisPreparationResult(
        Long userId,
        Long mockApplyId,
        JobPosting jobPosting,
        List<Question> questions,
        List<Question> answeredQuestions,
        JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
        RetrievalContext retrievalContext,
        List<SimilarJobPostingContext> similarJobPostings
) {
    public AnalysisPreparationResult {
        questions = questions == null ? List.of() : List.copyOf(questions);
        answeredQuestions = answeredQuestions == null ? List.of() : List.copyOf(answeredQuestions);
        similarJobPostings = similarJobPostings == null ? List.of() : List.copyOf(similarJobPostings);
    }

    public AnalysisExecutionPayload toExecutionPayload() {
        return new AnalysisExecutionPayload(
                userId,
                mockApplyId,
                jobPosting,
                questions,
                answeredQuestions,
                jobCategoryEvaluationCriteria,
                retrievalContext,
                similarJobPostings
        );
    }
}
