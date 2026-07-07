package com.jobdri.jobdri_api.domain.jobposting.repository;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobPostingAsyncTaskRepository extends JpaRepository<JobPostingAsyncTask, String> {
    Optional<JobPostingAsyncTask> findByTaskIdAndUserId(String taskId, Long userId);

    List<JobPostingAsyncTask> findByStatusIn(Collection<TaskStatus> statuses);
}
