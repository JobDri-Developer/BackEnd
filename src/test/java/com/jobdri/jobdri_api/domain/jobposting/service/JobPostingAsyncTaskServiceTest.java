package com.jobdri.jobdri_api.domain.jobposting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingAsyncTaskServiceTest {

    @Mock
    private JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;

    private JobPostingAsyncTaskService jobPostingAsyncTaskService;

    @BeforeEach
    void setUp() {
        jobPostingAsyncTaskService = new JobPostingAsyncTaskService(jobPostingAsyncTaskRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("일반 사용자는 본인 소유 task만 조회할 수 있다")
    void getTaskAllowsOwnerOnly() {
        User user = User.signup("테스트 사용자", "job-posting-owner-task@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.getTask(user, task.getTaskId());

        assertThat(response.getTaskId()).isEqualTo(task.getTaskId());
        verify(jobPostingAsyncTaskRepository).findByTaskIdAndUserId(task.getTaskId(), 7L);
    }

    @Test
    @DisplayName("일반 사용자는 다른 사람 task를 조회할 수 없다")
    void getTaskRejectsNonOwner() {
        User user = User.signup("테스트 사용자", "job-posting-other-task@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId("task-1", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingAsyncTaskService.getTask(user, "task-1"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("관리자는 소유자와 관계없이 모든 task를 조회할 수 있다")
    void getTaskAllowsAdmin() {
        User admin = User.signup("관리자", "admin-task@example.com", "encoded-password");
        ReflectionTestUtils.setField(admin, "id", 99L);
        admin.promoteToAdmin();
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.getTask(admin, task.getTaskId());

        assertThat(response.getTaskId()).isEqualTo(task.getTaskId());
        verify(jobPostingAsyncTaskRepository).findById(task.getTaskId());
    }

    @Test
    @DisplayName("대기 시간이 임계치를 넘은 task는 QUEUE_TIMEOUT으로 실패 처리한다")
    void getTaskMarksPendingTimeout() {
        ReflectionTestUtils.setField(jobPostingAsyncTaskService, "queueTimeoutMinutes", 1L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        ReflectionTestUtils.setField(task, "submittedAt", java.time.LocalDateTime.now().minusMinutes(2));

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.getTask(task.getTaskId());

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getFailureReason()).isEqualTo(FailureReason.QUEUE_TIMEOUT.name());
    }

    @Test
    @DisplayName("실행 중 시간이 임계치를 넘은 task는 WORKER_TIMEOUT으로 실패 처리한다")
    void getTaskMarksRunningTimeout() {
        ReflectionTestUtils.setField(jobPostingAsyncTaskService, "processingTimeoutMinutes", 1L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        task.markRunning("worker-1", 1, java.time.Instant.now().minusSeconds(120));
        ReflectionTestUtils.setField(task, "lastAttemptAt", java.time.LocalDateTime.now().minusMinutes(2));

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.getTask(task.getTaskId());

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getFailureReason()).isEqualTo(FailureReason.WORKER_TIMEOUT.name());
    }

    @Test
    @DisplayName("재시도 횟수가 최대값에 도달하면 FAILED로 전이한다")
    void markRetryScheduledFailsWhenMaxRetryReached() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.markRetryScheduled(
                task.getTaskId(),
                FailureReason.INTERNAL_ERROR,
                "retry exhausted",
                3
        );

        assertThat(task.getStatus()).isEqualTo(JobPostingAsyncTask.TaskStatus.FAILED);
        assertThat(task.getFailureReason()).isEqualTo(FailureReason.INTERNAL_ERROR);
        assertThat(task.getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("worker 메타데이터를 별도로 갱신할 수 있다")
    void updateWorkerMetadataUpdatesWorkerFields() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.updateWorkerMetadata(task.getTaskId(), "worker-2", 1234L);

        assertThat(task.getWorkerId()).isEqualTo("worker-2");
        assertThat(task.getQueueLatencyMillis()).isEqualTo(1234L);
    }
}
