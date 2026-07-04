package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobPostingAsyncTaskService {

    private final JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String createPendingTask() {
        JobPostingAsyncTask task = jobPostingAsyncTaskRepository.save(JobPostingAsyncTask.pending());
        return task.getTaskId();
    }

    @Transactional
    public void deleteTask(String taskId) {
        jobPostingAsyncTaskRepository.deleteById(taskId);
    }

    @Transactional
    public void markRunning(String taskId) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (isTerminal(task)) {
            return;
        }
        task.markRunning();
    }

    @Transactional
    public JobPostingIngestResponse markSuccess(String taskId, JobPostingIngestResponse result) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return deserializeResult(task.getResultPayload());
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 실패 처리된 채용 공고 비동기 작업입니다. taskId=" + taskId
            );
        }
        task.markSuccess(serializeResult(result));
        return result;
    }

    @Transactional
    public void markFailed(String taskId, String errorMessage) {
        JobPostingAsyncTask task = getTaskState(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return;
        }
        task.markFailed(errorMessage);
    }

    @Transactional(readOnly = true)
    public JobPostingAsyncStatusResponse getTask(String taskId) {
        JobPostingAsyncTask taskState = getTaskState(taskId);
        return JobPostingAsyncStatusResponse.builder()
                .taskId(taskState.getTaskId())
                .status(taskState.getStatus().name())
                .message(taskState.getMessage())
                .error(taskState.getError())
                .createdAt(taskState.getCreatedAt())
                .startedAt(taskState.getStartedAt())
                .completedAt(taskState.getCompletedAt())
                .result(deserializeResult(taskState.getResultPayload()))
                .build();
    }

    private JobPostingAsyncTask getTaskState(String taskId) {
        return jobPostingAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_ASYNC_TASK_NOT_FOUND,
                        "해당 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private boolean isTerminal(JobPostingAsyncTask task) {
        return task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED;
    }

    private String serializeResult(JobPostingIngestResponse result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "채용 공고 비동기 결과 직렬화에 실패했습니다."
            );
        }
    }

    private JobPostingIngestResponse deserializeResult(String resultPayload) {
        if (resultPayload == null || resultPayload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultPayload, JobPostingIngestResponse.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "채용 공고 비동기 결과 역직렬화에 실패했습니다."
            );
        }
    }
}
