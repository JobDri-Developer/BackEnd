package com.jobdri.jobdri_api.domain.workerresult.repository;

import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerTaskResultRepository extends JpaRepository<WorkerTaskResult, String> {
}
