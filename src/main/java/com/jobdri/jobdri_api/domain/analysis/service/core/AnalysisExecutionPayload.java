package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;

import java.util.List;

// 실제 분석 실행에 필요한 공고, 문항, 답변 데이터를 묶어 전달하는 payload다.
public record AnalysisExecutionPayload(
        Long userId,
        Long mockApplyId,
        JobPosting jobPosting,
        List<Question> questions,
        List<Question> answeredQuestions,
        JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
) {
    public AnalysisExecutionPayload(
            Long userId,
            Long mockApplyId,
            JobPosting jobPosting,
            List<Question> questions,
            List<Question> answeredQuestions
    ) {
        this(userId, mockApplyId, jobPosting, questions, answeredQuestions, null);
    }
}
