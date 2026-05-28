package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    Optional<Analysis> findByMockApplyId(Long mockApplyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Analysis a
            where a.mockApply.jobPosting.id = :jobPostingId
            """)
    void deleteAllByJobPostingId(@Param("jobPostingId") Long jobPostingId);
}
