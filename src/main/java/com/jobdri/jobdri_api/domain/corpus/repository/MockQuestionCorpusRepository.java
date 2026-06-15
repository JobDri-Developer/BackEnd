package com.jobdri.jobdri_api.domain.corpus.repository;

import com.jobdri.jobdri_api.domain.corpus.entity.MockQuestionCorpus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MockQuestionCorpusRepository extends JpaRepository<MockQuestionCorpus, Long> {
    Optional<MockQuestionCorpus> findBySourceQuestionId(String sourceQuestionId);

    List<MockQuestionCorpus> findAllBySourceAnalysisId(String sourceAnalysisId);

    List<MockQuestionCorpus> findAllByCompanyId(Long companyId);

    List<MockQuestionCorpus> findAllByValidForEmbeddingTrueAndEmbeddingTextIsNotNullOrderByIdAsc();
}
