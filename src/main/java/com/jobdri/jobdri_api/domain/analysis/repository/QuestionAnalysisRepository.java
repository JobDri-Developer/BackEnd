package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionAnalysisRepository extends JpaRepository<QuestionAnalysis, Long> {
    List<QuestionAnalysis> findAllByQuestionId(Long questionId);
    List<QuestionAnalysis> findAllByAnalysisId(Long analysisId);
}
