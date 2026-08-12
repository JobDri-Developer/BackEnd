package com.jobdri.jobdri_api.domain.analysis.repository;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select task.taskId
            from AnalysisAsyncTask task
            where task.status = 'PENDING'
              and task.submittedAt is not null
              and task.submittedAt <= :deadline
            order by task.submittedAt asc
            """)
    List<String> findTimedOutPendingTaskIds(@Param("deadline") java.time.LocalDateTime deadline, Pageable pageable);

    @Query("""
            select task.taskId
            from AnalysisAsyncTask task
            where task.status = 'RUNNING'
              and (
                (task.lastAttemptAt is not null and task.lastAttemptAt <= :deadline)
                or (task.lastAttemptAt is null and task.startedAt is not null and task.startedAt <= :deadline)
              )
            order by coalesce(task.lastAttemptAt, task.startedAt) asc
            """)
    List<String> findTimedOutRunningTaskIds(@Param("deadline") java.time.LocalDateTime deadline, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AnalysisAsyncTask task where task.taskId = :taskId")
    Optional<AnalysisAsyncTask> findByIdForUpdate(@Param("taskId") String taskId);
}
