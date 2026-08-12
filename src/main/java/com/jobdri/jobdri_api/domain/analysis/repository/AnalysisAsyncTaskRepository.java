package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisAsyncTaskRepository extends JpaRepository<AnalysisAsyncTask, String> {
    Optional<AnalysisAsyncTask> findByTaskIdAndUserId(String taskId, Long userId);

    Optional<AnalysisAsyncTask> findFirstByUserIdAndMockApplyIdAndStatusInOrderByCreatedAtDesc(
            Long userId,
            Long mockApplyId,
            Collection<AnalysisAsyncTaskStatus> statuses
    );

    Optional<AnalysisAsyncTask> findFirstByUserIdAndMockApplyIdAndStatusAndFailureReasonOrderByCreatedAtDesc(
            Long userId,
            Long mockApplyId,
            AnalysisAsyncTaskStatus status,
            AnalysisAsyncFailureReason failureReason
    );

    List<AnalysisAsyncTask> findByUserIdAndMockApplyIdInAndStatusIn(
            Long userId,
            Collection<Long> mockApplyIds,
            Collection<AnalysisAsyncTaskStatus> statuses
    );

    List<AnalysisAsyncTask> findByStatusIn(Collection<AnalysisAsyncTaskStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AnalysisAsyncTask task where task.taskId = :taskId")
    Optional<AnalysisAsyncTask> findByIdForUpdate(@Param("taskId") String taskId);
}
