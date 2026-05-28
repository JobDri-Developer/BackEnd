package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionAnalysisRepository extends JpaRepository<QuestionAnalysis, Long> {
    List<QuestionAnalysis> findAllByQuestionId(Long questionId);
    List<QuestionAnalysis> findAllByAnalysisId(Long analysisId);
    List<QuestionAnalysis> findAllByAnalysisIdOrderByQuestionIdAscIdAsc(Long analysisId);
    void deleteAllByAnalysisId(Long analysisId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from QuestionAnalysis qa
            where qa.analysis.id in (
                select a.id
                from Analysis a
                where a.mockApply.jobPosting.id = :jobPostingId
            )
            """)
    void deleteAllByJobPostingId(@Param("jobPostingId") Long jobPostingId);
}
