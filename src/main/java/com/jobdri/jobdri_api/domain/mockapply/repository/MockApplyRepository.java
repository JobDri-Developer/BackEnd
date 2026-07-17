package com.jobdri.jobdri_api.domain.mockapply.repository;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MockApplyRepository extends JpaRepository<MockApply, Long> {
    List<MockApply> findAllByUserId(Long userId);
    List<MockApply> findAllByJobPostingId(Long jobPostingId);
    List<MockApply> findAllByUserIdAndJobPostingIdOrderByIdAsc(Long userId, Long jobPostingId);
    long countByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MockApply ma
            where ma.jobPosting.id = :jobPostingId
            """)
    void deleteAllByJobPostingId(Long jobPostingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MockApply ma
            where ma.id = :mockApplyId
            """)
    void deleteByMockApplyId(@Param("mockApplyId") Long mockApplyId);

    @Query("""
            select ma
            from MockApply ma
            join fetch ma.user
            join fetch ma.jobPosting jp
            join fetch jp.company
            join fetch jp.detailClassification
            where ma.id = :mockApplyId
            """)
    Optional<MockApply> findByIdWithJobPosting(@Param("mockApplyId") Long mockApplyId);

    @Query("""
            select coalesce(max(ma.sequence), 0)
            from MockApply ma
            where ma.user.id = :userId
              and ma.jobPosting.id = :jobPostingId
            """)
    int findMaxSequenceByUserIdAndJobPostingId(
            @Param("userId") Long userId,
            @Param("jobPostingId") Long jobPostingId
    );

    @Query("""
            select coalesce(max(ma.sequence), 0)
            from MockApply ma
            where ma.user.id = :userId
              and ma.jobPosting.company.id = :companyId
              and ma.jobPosting.detailClassification.id = :detailClassificationId
            """)
    int findMaxSequenceByUserIdAndCompanyIdAndDetailClassificationId(
            @Param("userId") Long userId,
            @Param("companyId") Long companyId,
            @Param("detailClassificationId") Long detailClassificationId
    );

    default int calculateSequence(MockApply mockApply) {
        if (mockApply.getSequence() != null && mockApply.getSequence() > 0) {
            return mockApply.getSequence();
        }

        return Math.toIntExact(countSequenceByUserIdAndJobPostingId(
                mockApply.getUser().getId(),
                mockApply.getJobPosting().getId(),
                mockApply.getId()
        ));
    }

    @Query("""
            select ma
            from MockApply ma
            join fetch ma.jobPosting jp
            join fetch jp.company
            join fetch jp.detailClassification
            left join fetch ma.analysis
            where ma.user.id = :userId
            order by ma.createdAt desc, ma.id desc
            """)
    List<MockApply> findHomeItemsByUserId(@Param("userId") Long userId);

    @Query("""
            select count(ma)
            from MockApply ma
            where ma.user.id = :userId
              and ma.jobPosting.id = :jobPostingId
              and ma.id <= :mockApplyId
            """)
    long countSequenceByUserIdAndJobPostingId(
            @Param("userId") Long userId,
            @Param("jobPostingId") Long jobPostingId,
            @Param("mockApplyId") Long mockApplyId
    );
}
