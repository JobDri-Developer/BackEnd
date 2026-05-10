package com.jobdri.jobdri_api.domain.jobposting.repository;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    List<JobPosting> findAllByCompanyId(Long companyId);
    List<JobPosting> findAllByDetailClassificationId(Long detailClassificationId);
}
