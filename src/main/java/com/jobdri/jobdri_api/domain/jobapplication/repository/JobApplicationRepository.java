package com.jobdri.jobdri_api.domain.jobapplication.repository;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("""
            select coalesce(max(ja.stageOrder), -1)
            from JobApplication ja
            where ja.user.id = :userId
              and ja.stage = :stage
              and ja.archivedAt is null
            """)
    int findMaxStageOrder(@Param("userId") Long userId, @Param("stage") JobApplicationStage stage);

    @EntityGraph(attributePaths = {"detailClassification", "requiredSkills"})
    Optional<JobApplication> findDetailedById(Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update JobApplication ja set ja.sourceJobPosting = null where ja.sourceJobPosting.id = :jobPostingId")
    int clearSourceJobPosting(@Param("jobPostingId") Long jobPostingId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update JobApplication ja set ja.mockApply = null where ja.mockApply.jobPosting.id = :jobPostingId")
    int clearMockAppliesForJobPosting(@Param("jobPostingId") Long jobPostingId);
}
