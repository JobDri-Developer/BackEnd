package com.jobdri.jobdri_api.domain.workerresult.entity;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "worker_task_results")
public class WorkerTaskResult extends BaseEntity {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "result_payload", nullable = false, columnDefinition = "TEXT")
    private String resultPayload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public static WorkerTaskResult generated(String taskId, TaskType taskType, String resultPayload) {
        WorkerTaskResult result = new WorkerTaskResult();
        result.taskId = taskId;
        result.taskType = taskType;
        result.status = DeliveryStatus.GENERATED;
        result.resultPayload = resultPayload;
        result.attemptCount = 1;
        return result;
    }

    public void overwriteGenerated(TaskType taskType, String resultPayload) {
        validateTaskType(taskType);
        this.taskType = taskType;
        this.status = DeliveryStatus.GENERATED;
        this.resultPayload = resultPayload;
        this.attemptCount += 1;
        this.lastError = null;
    }

    public void markDelivered(TaskType taskType) {
        validateTaskType(taskType);
        this.status = DeliveryStatus.DELIVERED;
        this.lastError = null;
    }

    public void markDeliveryFailed(TaskType taskType, String errorMessage) {
        validateTaskType(taskType);
        this.status = DeliveryStatus.GENERATED;
        this.lastError = errorMessage;
    }

    private void validateTaskType(TaskType taskType) {
        if (this.taskType != taskType) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "저장된 worker 결과 taskType이 일치하지 않습니다. taskId=" + taskId
            );
        }
    }

    public enum TaskType {
        ANALYSIS_COMPLETE,
        JOB_POSTING_COMPLETE,
        JOB_POSTING_FINALIZE
    }

    public enum DeliveryStatus {
        GENERATED,
        DELIVERED
    }
}
