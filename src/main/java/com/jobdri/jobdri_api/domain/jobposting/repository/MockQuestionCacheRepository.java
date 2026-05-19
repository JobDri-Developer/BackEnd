package com.jobdri.jobdri_api.domain.jobposting.repository;

import com.jobdri.jobdri_api.domain.jobposting.entity.MockQuestionCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockQuestionCacheRepository extends JpaRepository<MockQuestionCache, Long> {
    Optional<MockQuestionCache> findByDetailClassification_IdAndPromptVersion(Long detailClassificationId, String promptVersion);
}
