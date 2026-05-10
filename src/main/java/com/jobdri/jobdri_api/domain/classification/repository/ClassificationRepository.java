package com.jobdri.jobdri_api.domain.classification.repository;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassificationRepository extends JpaRepository<Classification, Long> {
    Optional<Classification> findByBigName(String bigName);
}
