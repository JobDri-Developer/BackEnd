package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findAllByMockApplyId(Long mockApplyId);
    List<Question> findAllByMockApplyIdOrderByIdAsc(Long mockApplyId);
    boolean existsByMockApplyIdAndContent(Long mockApplyId, String content);
    void deleteAllByMockApplyId(Long mockApplyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Question q
            where q.mockApply.jobPosting.id = :jobPostingId
            """)
    void deleteAllByJobPostingId(@Param("jobPostingId") Long jobPostingId);
}
