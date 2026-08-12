package com.jobdri.jobdri_api.domain.analysis.application.model;

import com.jobdri.jobdri_api.domain.analysis.dto.internal.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;

import java.util.List;

// 실제 분석 실행에 필요한 공고, 문항, 답변 데이터를 묶어 전달하는 payload다.
public record AnalysisExecutionPayload(
        Long userId,
        Long mockApplyId,
        JobPosting jobPosting,
        List<Question> questions,
        List<Question> answeredQuestions,
        JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
        RetrievalContext retrievalContext,
        List<SimilarJobPostingContext> similarJobPostings,
        List<AnswerSnapshot> answerSnapshots
) {
    public AnalysisExecutionPayload(
            Long userId,
            Long mockApplyId,
            JobPosting jobPosting,
            List<Question> questions,
            List<Question> answeredQuestions
    ) {
        this(userId, mockApplyId, jobPosting, questions, answeredQuestions, null, null, List.of(), null);
    }

    public AnalysisExecutionPayload(
            Long userId,
            Long mockApplyId,
            JobPosting jobPosting,
            List<Question> questions,
            List<Question> answeredQuestions,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        this(userId, mockApplyId, jobPosting, questions, answeredQuestions, jobCategoryEvaluationCriteria, null, List.of(), null);
    }

    public AnalysisExecutionPayload(
            Long userId,
            Long mockApplyId,
            JobPosting jobPosting,
            List<Question> questions,
            List<Question> answeredQuestions,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            RetrievalContext retrievalContext
    ) {
        this(
                userId,
                mockApplyId,
                jobPosting,
                questions,
                answeredQuestions,
                jobCategoryEvaluationCriteria,
                retrievalContext,
                List.of(),
                null
        );
    }

    public AnalysisExecutionPayload(
            Long userId,
            Long mockApplyId,
            JobPosting jobPosting,
            List<Question> questions,
            List<Question> answeredQuestions,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            RetrievalContext retrievalContext,
            List<SimilarJobPostingContext> similarJobPostings
    ) {
        this(
                userId,
                mockApplyId,
                jobPosting,
                questions,
                answeredQuestions,
                jobCategoryEvaluationCriteria,
                retrievalContext,
                similarJobPostings,
                null
        );
    }

    public AnalysisExecutionPayload {
        questions = questions == null ? List.of() : List.copyOf(questions);
        answeredQuestions = answeredQuestions == null ? List.of() : List.copyOf(answeredQuestions);
        similarJobPostings = similarJobPostings == null ? List.of() : List.copyOf(similarJobPostings);
        answerSnapshots = answerSnapshots == null
                ? snapshotAnswers(answeredQuestions)
                : List.copyOf(answerSnapshots);
    }

    public AnalysisExecutionPayload withAnswerSnapshots(List<AnswerSnapshot> snapshots) {
        return new AnalysisExecutionPayload(
                userId,
                mockApplyId,
                jobPosting,
                questions,
                answeredQuestions,
                jobCategoryEvaluationCriteria,
                retrievalContext,
                similarJobPostings,
                snapshots
        );
    }

    public static List<AnswerSnapshot> snapshotAnswers(List<Question> questions) {
        if (questions == null) {
            return List.of();
        }
        return questions.stream()
                .filter(question -> question != null && question.getAnswer() != null && !question.getAnswer().isBlank())
                .map(question -> new AnswerSnapshot(question.getId(), question.getAnswer()))
                .toList();
    }

    public record AnswerSnapshot(Long questionId, String answer) {
        public AnswerSnapshot {
            answer = answer == null ? "" : answer;
        }
    }
}
