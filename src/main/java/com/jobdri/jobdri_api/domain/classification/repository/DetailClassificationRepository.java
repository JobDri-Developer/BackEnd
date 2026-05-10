package com.jobdri.jobdri_api.domain.classification.repository;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetailClassificationRepository extends JpaRepository<DetailClassification, Long> {
    List<DetailClassification> findAllByMiddleClassificationId(Long middleClassificationId);
    Optional<DetailClassification> findByDetailName(String detailName);
}
