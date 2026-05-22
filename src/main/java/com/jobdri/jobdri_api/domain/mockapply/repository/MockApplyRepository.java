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
