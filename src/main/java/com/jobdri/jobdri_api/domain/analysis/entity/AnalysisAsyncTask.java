package com.jobdri.jobdri_api.domain.analysis.entity;

import com.jobdri.jobdri_api.global.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_async_tasks")
public class AnalysisAsyncTask extends CreatedAtEntity {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mock_apply_id", nullable = false)
    private Long mockApplyId;

    @Column(name = "credit_reference_id", length = 100)
    private String creditReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_status", nullable = false, length = 20)
    private CreditStatus creditStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(length = 2000)
    private String error;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private FailureReason failureReason;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private int maxRetryCount;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "queue_latency_millis")
    private Long queueLatencyMillis;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "current_step", length = 60)
    private String currentStep;

    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "estimated_remaining_seconds")
    private Integer estimatedRemainingSeconds;

    @Column(name = "execution_context_snapshot", columnDefinition = "TEXT")
    private String executionContextSnapshot;

    @Column(name = "input_fingerprint_snapshot", length = 64)
    private String inputFingerprintSnapshot;

    public static AnalysisAsyncTask pending(Long userId, Long mockApplyId, int maxRetryCount) {
        AnalysisAsyncTask task = new AnalysisAsyncTask();
        task.taskId = UUID.randomUUID().toString();
        task.userId = userId;
        task.mockApplyId = mockApplyId;
        task.creditStatus = CreditStatus.NONE;
        task.status = TaskStatus.PENDING;
        task.message = "자소서 분석 비동기 작업이 접수되었습니다.";
        task.retryCount = 0;
        task.maxRetryCount = Math.max(0, maxRetryCount);
        task.submittedAt = LocalDateTime.now();
        task.cancelRequested = false;
        task.currentStep = "VALIDATING_INPUT";
        task.progressPercent = 0;
        return task;
    }

    public void markCreditReserved(String creditReferenceId) {
        this.creditReferenceId = creditReferenceId;
        this.creditStatus = CreditStatus.RESERVED;
    }

    public void markCreditConfirmed() {
        this.creditStatus = CreditStatus.CONFIRMED;
    }

    public void markCreditReleased() {
        this.creditStatus = CreditStatus.RELEASED;
    }

    public void markRunning(String workerId, int retryCount, Instant messageSubmittedAt) {
        if (isTerminal()) {
            return;
        }
        this.status = TaskStatus.RUNNING;
        this.message = "자소서 분석을 진행 중입니다.";
        this.currentStep = "PREPARING_CONTEXT";
        this.progressPercent = Math.max(resolveProgressPercent(), 10);
        this.failureReason = null;
        this.error = null;
        this.workerId = workerId;
        this.retryCount = Math.max(0, retryCount);
        this.lastAttemptAt = LocalDateTime.now();
        this.startedAt = this.lastAttemptAt;
        if (messageSubmittedAt != null) {
            this.queueLatencyMillis = Math.max(0L, Duration.between(messageSubmittedAt, Instant.now()).toMillis());
        }
    }

    public void markSuccess() {
        if (isTerminal()) {
            return;
        }
        this.status = TaskStatus.SUCCEEDED;
        this.message = "자소서 분석이 완료되었습니다.";
        this.error = null;
        this.failureReason = null;
        this.completedAt = LocalDateTime.now();
        this.currentStep = "COMPLETED";
        this.progressPercent = 100;
        this.estimatedRemainingSeconds = 0;
    }

    public void markRetryScheduled(FailureReason failureReason, String errorMessage, int retryCount) {
        if (isTerminal()) {
            return;
        }
        if (retryCount >= maxRetryCount) {
            markFailed(failureReason, errorMessage, retryCount);
            return;
        }
        this.status = TaskStatus.PENDING;
        this.message = "자소서 분석 재시도를 대기 중입니다.";
        this.currentStep = "VALIDATING_INPUT";
        this.progressPercent = 0;
        this.failureReason = failureReason;
        this.error = errorMessage;
        this.retryCount = Math.max(0, retryCount);
        this.completedAt = null;
    }

    public void markFailed(FailureReason failureReason, String errorMessage, int retryCount) {
        if (isTerminal()) {
            return;
        }
        this.status = TaskStatus.FAILED;
        this.message = "자소서 분석에 실패했습니다.";
        this.failureReason = failureReason;
        this.error = errorMessage;
        this.retryCount = Math.max(0, retryCount);
        this.completedAt = LocalDateTime.now();
        this.progressPercent = 0;
        this.estimatedRemainingSeconds = 0;
    }

    public void requestCancel() {
        if (status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED) {
            return;
        }
        this.cancelRequested = true;
        if (status == TaskStatus.CANCELLED) {
            if (cancelledAt == null) {
                this.cancelledAt = LocalDateTime.now();
            }
            return;
        }
        this.status = TaskStatus.CANCELLED;
        this.message = "자소서 분석 작업이 취소되었습니다.";
        this.error = null;
        this.failureReason = null;
        this.completedAt = LocalDateTime.now();
        this.cancelledAt = this.completedAt;
        this.progressPercent = 0;
        this.estimatedRemainingSeconds = 0;
    }

    public void updateWorkerMetadata(String workerId, Long queueLatencyMillis) {
        if (workerId != null && !workerId.isBlank()) {
            this.workerId = workerId;
        }
        if (queueLatencyMillis != null) {
            this.queueLatencyMillis = Math.max(0L, queueLatencyMillis);
        }
    }

    public void captureExecutionSnapshot(String executionContextSnapshot, String inputFingerprintSnapshot) {
        if (this.executionContextSnapshot != null || this.inputFingerprintSnapshot != null) {
            return;
        }
        this.executionContextSnapshot = executionContextSnapshot;
        this.inputFingerprintSnapshot = inputFingerprintSnapshot;
    }

    private boolean isTerminal() {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }

    private int resolveProgressPercent() {
        return progressPercent == null ? 0 : progressPercent;
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public enum CreditStatus {
        NONE,
        RESERVED,
        CONFIRMED,
        RELEASED
    }

    public enum FailureReason {
        RATE_LIMIT,
        QUEUE_TIMEOUT,
        OPENAI_TIMEOUT,
        VALIDATION_ERROR,
        INTERNAL_ERROR
    }
}
