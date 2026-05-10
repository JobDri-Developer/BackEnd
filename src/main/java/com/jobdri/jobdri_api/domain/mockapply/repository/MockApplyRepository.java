package com.jobdri.jobdri_api.domain.mockapply.repository;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockApplyRepository extends JpaRepository<MockApply, Long> {
    List<MockApply> findAllByUserId(Long userId);
    List<MockApply> findAllByJobPostingId(Long jobPostingId);
}
