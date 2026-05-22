package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.CustomQuestionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomQuestionCandidateRepository extends JpaRepository<CustomQuestionCandidate, Long> {
    List<CustomQuestionCandidate> findAllByMockApplyIdOrderByIdAsc(Long mockApplyId);
    Optional<CustomQuestionCandidate> findByMockApplyIdAndContent(Long mockApplyId, String content);
}
