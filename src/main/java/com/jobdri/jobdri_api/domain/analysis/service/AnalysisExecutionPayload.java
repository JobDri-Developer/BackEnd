package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;

import java.util.List;

// 실제 분석 실행에 필요한 공고, 문항, 답변 데이터를 묶어 전달하는 payload다.
public record AnalysisExecutionPayload(
        Long userId,
        Long mockApplyId,
        JobPosting jobPosting,
        List<Question> questions,
        List<Question> answeredQuestions
) {
}
