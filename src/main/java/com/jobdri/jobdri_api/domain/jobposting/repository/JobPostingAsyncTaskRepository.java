package com.jobdri.jobdri_api.domain.jobposting.repository;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingAsyncTaskRepository extends JpaRepository<JobPostingAsyncTask, String> {
}
