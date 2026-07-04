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

    public static AnalysisAsyncTask pending(Long userId, Long mockApplyId) {
        AnalysisAsyncTask task = new AnalysisAsyncTask();
        task.taskId = UUID.randomUUID().toString();
        task.userId = userId;
        task.mockApplyId = mockApplyId;
        task.status = TaskStatus.PENDING;
        task.message = "자소서 분석 비동기 작업이 접수되었습니다.";
        return task;
    }

    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.message = "자소서 분석을 진행 중입니다.";
        this.startedAt = LocalDateTime.now();
    }

    public void markSuccess() {
        this.status = TaskStatus.SUCCEEDED;
        this.message = "자소서 분석이 완료되었습니다.";
        this.error = null;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.message = "자소서 분석에 실패했습니다.";
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
