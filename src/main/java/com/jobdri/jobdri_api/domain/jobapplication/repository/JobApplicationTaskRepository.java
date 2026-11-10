package com.jobdri.jobdri_api.domain.jobapplication.repository;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationTask;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationTaskRepository extends JpaRepository<JobApplicationTask, Long> {
    @EntityGraph(attributePaths = "checklistItems")
    List<JobApplicationTask> findAllByJobApplicationIdOrderByIdAsc(Long jobApplicationId);

    Optional<JobApplicationTask> findByIdAndJobApplicationId(Long id, Long jobApplicationId);
}
