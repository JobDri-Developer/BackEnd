package com.jobdri.jobdri_api.domain.jobposting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingIngestResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationTargetType;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationType;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.notification.service.NotificationService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingAsyncTaskServiceTest {

    @Mock
    private JobPostingAsyncTaskRepository jobPostingAsyncTaskRepository;

    @Mock
    private JobPostingAsyncSseService jobPostingAsyncSseService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AsyncMetricsRecorder asyncMetricsRecorder;

    private JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private JobPostingQueueProperties jobPostingQueueProperties;

    @BeforeEach
    void setUp() {
        jobPostingQueueProperties = new JobPostingQueueProperties();
        jobPostingAsyncTaskService = new JobPostingAsyncTaskService(
                jobPostingAsyncTaskRepository,
                new ObjectMapper(),
                jobPostingAsyncSseService,
                notificationService,
                asyncMetricsRecorder,
                jobPostingQueueProperties,
                new AsyncProgressCalculator()
        );
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
        jobPostingQueueProperties.setQueueTimeoutMinutes(1L);
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
        jobPostingQueueProperties.setProcessingTimeoutMinutes(1L);
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
        verify(jobPostingAsyncSseService).publish(any());
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

    @Test
    @DisplayName("이미 종료된 task에 대한 재시도 예약은 무시된다")
    void markRetryScheduledDoesNothingWhenTaskIsTerminal() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        task.markFailed(FailureReason.INTERNAL_ERROR, "failed", 1);
        String originalError = task.getError();
        int originalRetryCount = task.getRetryCount();

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.markRetryScheduled(
                task.getTaskId(),
                FailureReason.QUEUE_TIMEOUT,
                "should be ignored",
                2
        );

        assertThat(task.getFailureReason()).isEqualTo(FailureReason.INTERNAL_ERROR);
        assertThat(task.getError()).isEqualTo(originalError);
        assertThat(task.getRetryCount()).isEqualTo(originalRetryCount);
        verify(jobPostingAsyncSseService, never()).publish(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("성공 처리 시 알림 서비스를 호출한다")
    void markSuccessCreatesNotification() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        JobPostingResponse saved = JobPostingResponse.builder()
                .jobPostingId(123L)
                .build();
        JobPostingIngestResponse result = new JobPostingIngestResponse(true, null, null, null, null, null, saved);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.markSuccess(task.getTaskId(), result);

        verify(notificationService).createNotification(
                eq(7L),
                eq(NotificationType.JOB_POSTING_ASYNC_SUCCEEDED),
                eq("채용 공고 작업이 완료되었습니다."),
                eq("채용 공고 분석과 저장이 완료되었습니다."),
                eq(NotificationTargetType.JOB_POSTING_RESULT),
                eq("123"),
                any()
        );
    }

    @Test
    @DisplayName("이미 성공한 task의 complete 재호출은 기존 결과를 반환한다")
    void markSuccessReturnsExistingResultWhenTaskAlreadySucceeded() throws Exception {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        JobPostingIngestResponse existing = new JobPostingIngestResponse(true, "done", null, null, null, null, null);
        task.markSuccess(new ObjectMapper().writeValueAsString(existing));

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        JobPostingIngestResponse replayed = new JobPostingIngestResponse(false, "ignored", null, null, null, null, null);

        JobPostingIngestResponse response = jobPostingAsyncTaskService.markSuccess(task.getTaskId(), replayed);

        assertThat(response.isSavedToDatabase()).isTrue();
        assertThat(response.getMessage()).isEqualTo("done");
        verify(jobPostingAsyncSseService, never()).publish(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("실패 처리 시 사용자에게는 일반화된 실패 알림을 보낸다")
    void markFailedCreatesSanitizedNotification() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.markFailed(
                task.getTaskId(),
                FailureReason.INTERNAL_ERROR,
                "worker stack trace",
                1
        );

        verify(notificationService).createNotification(
                eq(7L),
                eq(NotificationType.JOB_POSTING_ASYNC_FAILED),
                eq("채용 공고 작업이 실패했습니다."),
                eq("채용 공고 작업 처리 중 오류가 발생했습니다."),
                eq(NotificationTargetType.JOB_POSTING_TASK),
                eq(task.getTaskId()),
                any()
        );
    }

    @Test
    @DisplayName("본인 소유 task 취소 시 CANCELLED 상태와 진행 정보를 발행한다")
    void cancelTaskMarksCancelledAndPublishesStatus() {
        User user = User.signup("테스트 사용자", "job-posting-cancel@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.cancelTask(user, task.getTaskId());

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(task.getStatus()).isEqualTo(JobPostingAsyncTask.TaskStatus.CANCELLED);
        assertThat(task.isCancelRequested()).isTrue();
        verify(jobPostingAsyncSseService).publish(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("본인 소유가 아닌 task 취소는 거부한다")
    void cancelTaskRejectsNonOwner() {
        User user = User.signup("테스트 사용자", "job-posting-cancel-forbidden@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId("task-1", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingAsyncTaskService.cancelTask(user, "task-1"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("이미 성공한 task 취소는 상태와 메시지를 유지하고 SSE를 발행하지 않는다")
    void cancelTaskKeepsSucceededTaskWithoutPublish() throws Exception {
        User user = User.signup("테스트 사용자", "job-posting-cancel-succeeded@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        task.markSuccess(new ObjectMapper().writeValueAsString(new JobPostingIngestResponse(true, "done", null, null, null, null, null)));

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.cancelTask(user, task.getTaskId());

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(task.getStatus()).isEqualTo(JobPostingAsyncTask.TaskStatus.SUCCEEDED);
        assertThat(task.getMessage()).isEqualTo("채용 공고 비동기 처리에 성공했습니다.");
        verify(jobPostingAsyncSseService, never()).publish(any());
    }

    @Test
    @DisplayName("이미 실패한 task 취소는 상태와 메시지를 유지하고 SSE를 발행하지 않는다")
    void cancelTaskKeepsFailedTaskWithoutPublish() {
        User user = User.signup("테스트 사용자", "job-posting-cancel-failed@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        task.markFailed(FailureReason.INTERNAL_ERROR, "failed", 1);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.cancelTask(user, task.getTaskId());

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(task.getStatus()).isEqualTo(JobPostingAsyncTask.TaskStatus.FAILED);
        assertThat(task.getMessage()).isEqualTo("채용 공고 비동기 처리에 실패했습니다.");
        verify(jobPostingAsyncSseService, never()).publish(any());
    }

    @Test
    @DisplayName("반복 취소는 기존 cancelledAt을 유지한다")
    void cancelTaskKeepsOriginalCancelledAtOnRepeat() {
        User user = User.signup("테스트 사용자", "job-posting-cancel-repeat@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.cancelTask(user, task.getTaskId());
        var firstCancelledAt = task.getCancelledAt();
        clearInvocations(jobPostingAsyncSseService);

        jobPostingAsyncTaskService.cancelTask(user, task.getTaskId());

        assertThat(task.getCancelledAt()).isEqualTo(firstCancelledAt);
        verify(jobPostingAsyncSseService, never()).publish(any());
    }

    @Test
    @DisplayName("RUNNING task 취소는 진행률을 0으로 초기화하고 종료 시각을 기록한다")
    void cancelTaskResetsRunningProgressAndSetsTerminalTimestamps() {
        User user = User.signup("테스트 사용자", "job-posting-cancel-running@example.com", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 7L);
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);
        task.markRunning("worker-1", 0, java.time.Instant.now());

        when(jobPostingAsyncTaskRepository.findByTaskIdAndUserId(task.getTaskId(), 7L)).thenReturn(Optional.of(task));

        var response = jobPostingAsyncTaskService.cancelTask(user, task.getTaskId());

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(task.getProgressPercent()).isZero();
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("재시도 예약 중에는 알림을 생성하지 않는다")
    void markRetryScheduledDoesNotCreateNotification() {
        JobPostingAsyncTask task = JobPostingAsyncTask.pending(7L, 3);

        when(jobPostingAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        jobPostingAsyncTaskService.markRetryScheduled(
                task.getTaskId(),
                FailureReason.INTERNAL_ERROR,
                "retry scheduled",
                1
        );

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }
}
