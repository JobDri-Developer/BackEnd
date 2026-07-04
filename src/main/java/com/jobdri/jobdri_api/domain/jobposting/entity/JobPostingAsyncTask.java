package com.jobdri.jobdri_api.domain.jobposting.entity;

import com.jobdri.jobdri_api.global.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "job_posting_async_tasks")
public class JobPostingAsyncTask extends CreatedAtEntity {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(length = 2000)
    private String error;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Lob
    @Column(name = "result_payload")
    private String resultPayload;

    public static JobPostingAsyncTask pending() {
        JobPostingAsyncTask task = new JobPostingAsyncTask();
        task.taskId = UUID.randomUUID().toString();
        task.status = TaskStatus.PENDING;
        task.message = "채용 공고 비동기 작업이 접수되었습니다.";
        return task;
    }

    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.message = "채용 공고 비동기 처리를 진행 중입니다.";
        this.startedAt = LocalDateTime.now();
    }

    public void markSuccess(String resultPayload) {
        this.status = TaskStatus.SUCCEEDED;
        this.message = "채용 공고 비동기 처리에 성공했습니다.";
        this.error = null;
        this.resultPayload = resultPayload;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.message = "채용 공고 비동기 처리에 실패했습니다.";
        this.error = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }
}
