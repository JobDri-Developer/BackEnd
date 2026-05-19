package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findAllByMockApplyId(Long mockApplyId);
    List<Question> findAllByMockApplyIdOrderByIdAsc(Long mockApplyId);
    void deleteAllByMockApplyId(Long mockApplyId);
}
