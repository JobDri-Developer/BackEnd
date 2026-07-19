package com.jobdri.jobdri_api.domain.workerresult.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.workerresult.dto.WorkerTaskResultResponse;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult;
import com.jobdri.jobdri_api.domain.workerresult.entity.WorkerTaskResult.TaskType;
import com.jobdri.jobdri_api.domain.workerresult.repository.WorkerTaskResultRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkerTaskResultService {

    private final WorkerTaskResultRepository workerTaskResultRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void upsertGenerated(TaskType taskType, String taskId, Object payload) {
        String serializedPayload = serialize(payload);
        workerTaskResultRepository.findById(taskId)
                .ifPresentOrElse(
                        existing -> existing.overwriteGenerated(taskType, serializedPayload),
                        () -> workerTaskResultRepository.save(WorkerTaskResult.generated(taskId, taskType, serializedPayload))
                );
    }

    @Transactional
    public void markDeliveredIfPresent(TaskType taskType, String taskId) {
        workerTaskResultRepository.findById(taskId)
                .ifPresent(result -> result.markDelivered(taskType));
    }

    @Transactional
    public void markDeliveryFailedIfPresent(TaskType taskType, String taskId, String errorMessage) {
        workerTaskResultRepository.findById(taskId)
                .ifPresent(result -> result.markDeliveryFailed(taskType, errorMessage));
    }

    @Transactional(readOnly = true)
    public WorkerTaskResultResponse get(String taskId) {
        WorkerTaskResult result = workerTaskResultRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.WORKER_TASK_RESULT_NOT_FOUND,
                        "해당 worker 결과를 찾을 수 없습니다. taskId=" + taskId
                ));
        return WorkerTaskResultResponse.from(result);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "worker 결과 직렬화에 실패했습니다."
            );
        }
    }
}
