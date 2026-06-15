package com.jobdri.jobdri_api.domain.corpus.repository;

import com.jobdri.jobdri_api.domain.corpus.entity.CorpusClassificationMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorpusClassificationMappingRepository extends JpaRepository<CorpusClassificationMapping, Long> {
    Optional<CorpusClassificationMapping> findBySourceJobGroupL1AndSourceJobFamilyL2AndSourceRoleL3(
            String sourceJobGroupL1,
            String sourceJobFamilyL2,
            String sourceRoleL3
    );
}
