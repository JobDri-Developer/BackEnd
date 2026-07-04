package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncTaskService {

    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;

    @Transactional
    public AnalysisAsyncTask createPendingTask(Long userId, Long mockApplyId) {
        return analysisAsyncTaskRepository.saveAndFlush(AnalysisAsyncTask.pending(userId, mockApplyId));
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
    public void markRunning(String taskId) {
        getTask(taskId).markRunning();
    }

    @Transactional
    public void markSuccess(String taskId) {
        getTask(taskId).markSuccess();
    }

    @Transactional
    public void markFailed(String taskId, String errorMessage) {
        getTask(taskId).markFailed(errorMessage);
    }

    @Transactional(readOnly = true)
    public AnalysisAsyncStatusResponse getTaskStatus(Long userId, String taskId) {
        AnalysisAsyncTask task = analysisAsyncTaskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));

        return AnalysisAsyncStatusResponse.builder()
                .taskId(task.getTaskId())
                .mockApplyId(task.getMockApplyId())
                .status(task.getStatus().name())
                .message(task.getMessage())
                .error(task.getError())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .result(null)
                .build();
    }

    private AnalysisAsyncTask getTask(String taskId) {
        return analysisAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }
}
