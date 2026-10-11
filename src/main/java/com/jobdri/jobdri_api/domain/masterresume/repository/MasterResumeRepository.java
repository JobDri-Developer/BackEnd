package com.jobdri.jobdri_api.domain.masterresume.repository;

import com.jobdri.jobdri_api.domain.masterresume.entity.MasterResume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MasterResumeRepository extends JpaRepository<MasterResume, Long> {
    Optional<MasterResume> findByUserId(Long userId);
}
