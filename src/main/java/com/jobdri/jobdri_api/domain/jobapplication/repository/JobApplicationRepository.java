package com.jobdri.jobdri_api.domain.jobapplication.repository;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("""
            select distinct ja from JobApplication ja left join fetch ja.requiredSkills
            where ja.user.id = :userId and ja.archivedAt is null
              and (lower(ja.companyName) like :pattern escape '!'
                or lower(ja.postingName) like :pattern escape '!'
                or lower(ja.jobTitle) like :pattern escape '!')
            """)
    List<JobApplication> findBoard(@Param("userId") Long userId, @Param("pattern") String pattern);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ja from JobApplication ja
            where ja.user.id = :userId and ja.archivedAt is null
              and ja.stage in :stages
            order by ja.stage, ja.stageOrder, ja.id
            """)
    List<JobApplication> lockActiveColumns(@Param("userId") Long userId,
                                           @Param("stages") List<JobApplicationStage> stages);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ja from JobApplication ja where ja.id = :id")
    Optional<JobApplication> findByIdForUpdate(@Param("id") Long id);

    @Query(
            value = """
                    select new com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse(
                        ja.id, ja.companyName, ja.postingName, ja.jobTitle, ja.deadlineAt,
                        ja.currentLabel, ja.currentAt, ja.stage, ja.archivedAt, ja.createdAt, ja.updatedAt
                    )
                    from JobApplication ja
                    where ja.user.id = :userId and ja.archivedAt is not null
                    """,
            countQuery = """
                    select count(ja)
                    from JobApplication ja
                    where ja.user.id = :userId and ja.archivedAt is not null
                    """
    )
    Page<JobApplicationArchiveItemResponse> findArchivePageByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update JobApplication ja set ja.sourceJobPosting = null where ja.sourceJobPosting.id = :jobPostingId")
    int clearSourceJobPosting(@Param("jobPostingId") Long jobPostingId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update JobApplication ja set ja.mockApply = null where ja.mockApply.jobPosting.id = :jobPostingId")
    int clearMockAppliesForJobPosting(@Param("jobPostingId") Long jobPostingId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update JobApplication ja set ja.mockApply = null where ja.mockApply.id = :mockApplyId")
    int clearMockApply(@Param("mockApplyId") Long mockApplyId);
}
