package com.jobdri.jobdri_api.domain.experience.repository;

import com.jobdri.jobdri_api.domain.experience.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    List<Experience> findAllByUserId(Long userId);
}
