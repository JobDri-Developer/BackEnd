package com.jobdri.jobdri_api.domain.classification.repository;

import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MiddleClassificationRepository extends JpaRepository<MiddleClassification, Long> {
    List<MiddleClassification> findAllByClassificationId(Long classificationId);
    Optional<MiddleClassification> findByMiddleName(String middleName);
}
