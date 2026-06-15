package com.jobdri.jobdri_api.domain.corpus.repository;

import com.jobdri.jobdri_api.domain.corpus.entity.MockJobPostingCorpus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MockJobPostingCorpusRepository extends JpaRepository<MockJobPostingCorpus, Long> {
    Optional<MockJobPostingCorpus> findBySourceAnalysisId(String sourceAnalysisId);

    List<MockJobPostingCorpus> findAllByCompanyId(Long companyId);

    List<MockJobPostingCorpus> findAllByValidForEmbeddingTrueAndEmbeddingTextIsNotNullOrderByIdAsc();
}
