package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.CreditStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncTaskService {

    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    
    @Value("${app.worker.analysis.max-retry-count:3}")
    private int maxRetryCount;

    @Transactional
    public AnalysisAsyncTask createPendingTask(Long userId, Long mockApplyId) {
        return analysisAsyncTaskRepository.saveAndFlush(
                AnalysisAsyncTask.pending(userId, mockApplyId, maxRetryCount)
        );
    }

    @Transactional
    public void deleteTask(String taskId) {
        analysisAsyncTaskRepository.deleteById(taskId);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisAsyncTask> findActiveTask(Long userId, Long mockApplyId) {
        return analysisAsyncTaskRepository.findFirstByUserIdAndMockApplyIdAndStatusInOrderByCreatedAtDesc(
                userId,
                mockApplyId,
                EnumSet.of(TaskStatus.PENDING, TaskStatus.RUNNING)
        );
    }

    @Transactional
    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        getTask(taskId).markRunning(workerId, retryCount, submittedAt);
    }

    @Transactional
    public void markSuccess(String taskId) {
        getTask(taskId).markSuccess();
    }

    @Transactional
    public void markRetryScheduled(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        getTask(taskId).markRetryScheduled(failureReason, errorMessage, retryCount);
    }

    @Transactional
    public void markFailed(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        getTask(taskId).markFailed(failureReason, errorMessage, retryCount);
    }

    @Transactional
    public void updateWorkerMetadata(String taskId, String workerId, Long queueLatencyMillis) {
        getTask(taskId).updateWorkerMetadata(workerId, queueLatencyMillis);
    }

    @Transactional
    public void markCreditReserved(String taskId, String creditReferenceId) {
        getTask(taskId).markCreditReserved(creditReferenceId);
    }

    @Transactional
    public void markCreditConfirmed(String taskId) {
        getTask(taskId).markCreditConfirmed();
    }

    @Transactional
    public void markCreditReleased(String taskId) {
        getTask(taskId).markCreditReleased();
    }

    @Transactional(readOnly = true)
    public CreditStatus getCreditStatus(String taskId) {
        return getTask(taskId).getCreditStatus();
    }

    @Transactional(readOnly = true)
    public AnalysisAsyncStatusResponse getTaskStatus(Long userId, String taskId) {
        AnalysisAsyncTask task = analysisAsyncTaskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
        return toStatusResponse(task);
    }

    @Transactional(readOnly = true)
    public AnalysisAsyncStatusResponse getTaskStatusByTaskId(String taskId) {
        return toStatusResponse(getTask(taskId));
    }

    private AnalysisAsyncTask getTask(String taskId) {
        return analysisAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private AnalysisAsyncStatusResponse toStatusResponse(AnalysisAsyncTask task) {
        return AnalysisAsyncStatusResponse.builder()
                .taskId(task.getTaskId())
                .mockApplyId(task.getMockApplyId())
                .status(task.getStatus().name())
                .message(task.getMessage())
                .error(task.getError())
                .failureReason(task.getFailureReason() != null ? task.getFailureReason().name() : null)
                .workerId(task.getWorkerId())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .queueLatencyMillis(task.getQueueLatencyMillis())
                .createdAt(task.getCreatedAt())
                .submittedAt(task.getSubmittedAt())
                .lastAttemptAt(task.getLastAttemptAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .result(null)
                .build();
    }
}
