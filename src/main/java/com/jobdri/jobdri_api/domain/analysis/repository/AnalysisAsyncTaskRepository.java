package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisAsyncTaskRepository extends JpaRepository<AnalysisAsyncTask, String> {
    Optional<AnalysisAsyncTask> findByTaskIdAndUserId(String taskId, Long userId);

    Optional<AnalysisAsyncTask> findFirstByUserIdAndMockApplyIdAndStatusInOrderByCreatedAtDesc(
            Long userId,
            Long mockApplyId,
            Collection<TaskStatus> statuses
    );

    List<AnalysisAsyncTask> findByUserIdAndMockApplyIdInAndStatusIn(
            Long userId,
            Collection<Long> mockApplyIds,
            Collection<TaskStatus> statuses
    );

    List<AnalysisAsyncTask> findByStatusIn(Collection<TaskStatus> statuses);
}
