package com.jobdri.jobdri_api.domain.mockapply.repository;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MockApplyRepository extends JpaRepository<MockApply, Long> {
    List<MockApply> findAllByUserId(Long userId);
    List<MockApply> findAllByJobPostingId(Long jobPostingId);
    long countByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    default int calculateSequence(MockApply mockApply) {
        return Math.toIntExact(countSequenceByUserIdAndJobPostingId(
                mockApply.getUser().getId(),
                mockApply.getJobPosting().getId(),
                mockApply.getCreatedAt(),
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
              and (
                    ma.createdAt < :createdAt
                    or (ma.createdAt = :createdAt and ma.id <= :mockApplyId)
                  )
            """)
    long countSequenceByUserIdAndJobPostingId(
            @Param("userId") Long userId,
            @Param("jobPostingId") Long jobPostingId,
            @Param("createdAt") java.time.LocalDateTime createdAt,
            @Param("mockApplyId") Long mockApplyId
    );
}
