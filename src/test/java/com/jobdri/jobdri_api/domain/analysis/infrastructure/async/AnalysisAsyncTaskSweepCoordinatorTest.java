package com.jobdri.jobdri_api.domain.analysis.infrastructure.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncTaskService;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisQueueProperties;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisAsyncTaskSweepCoordinatorTest {

    @Mock
    private AnalysisAsyncTaskRepository analysisAsyncTaskRepository;

    @Mock
    private AnalysisAsyncTaskService analysisAsyncTaskService;

    @Mock
    private AnalysisCreditService analysisCreditService;

    @Mock
    private UserService userService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AnalysisAsyncTaskSweepCoordinator analysisAsyncTaskSweepCoordinator;
    private AnalysisQueueProperties analysisQueueProperties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        analysisQueueProperties = new AnalysisQueueProperties();
        clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        analysisAsyncTaskSweepCoordinator = new AnalysisAsyncTaskSweepCoordinator(
                analysisAsyncTaskRepository,
                analysisAsyncTaskService,
                analysisCreditService,
                userService,
                transactionTemplate,
                analysisQueueProperties,
                clock
        );
        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("sweep는 timeout 대상 task id만 batch 조회해 처리한다")
    void sweepTimedOutTasksLoadsOnlyTimedOutTaskIdsInBatches() {
        LocalDateTime now = LocalDateTime.now(clock);
        AnalysisAsyncTask pendingTask = AnalysisAsyncTask.pending(1L, 10L, 3);
        ReflectionTestUtils.setField(pendingTask, "submittedAt", now.minusMinutes(10));
        AnalysisAsyncTask runningTask = AnalysisAsyncTask.pending(2L, 20L, 3);
        runningTask.markRunning("worker-1", 0, null);
        ReflectionTestUtils.setField(runningTask, "lastAttemptAt", now.minusMinutes(20));

        LocalDateTime pendingDeadline = now.minusSeconds(analysisQueueProperties.getQueueTimeoutSeconds());
        LocalDateTime runningDeadline = now.minusSeconds(analysisQueueProperties.getProcessingTimeoutSeconds());

        when(analysisAsyncTaskRepository.findTimedOutPendingTaskIds(eq(pendingDeadline), any(Pageable.class)))
                .thenReturn(List.of(pendingTask.getTaskId()), List.of());
        when(analysisAsyncTaskRepository.findTimedOutRunningTaskIds(eq(runningDeadline), any(Pageable.class)))
                .thenReturn(List.of(runningTask.getTaskId()), List.of());
        when(analysisAsyncTaskRepository.findByIdForUpdate(pendingTask.getTaskId())).thenReturn(Optional.of(pendingTask));
        when(analysisAsyncTaskRepository.findByIdForUpdate(runningTask.getTaskId())).thenReturn(Optional.of(runningTask));

        int expiredCount = analysisAsyncTaskSweepCoordinator.sweepTimedOutTasks();

        assertThat(expiredCount).isEqualTo(2);
        verify(analysisAsyncTaskService).markFailed(
                pendingTask.getTaskId(),
                AnalysisAsyncFailureReason.QUEUE_TIMEOUT,
                "자소서 분석 작업이 대기열에서 시간 내 처리되지 않았습니다.",
                pendingTask.getRetryCount()
        );
        verify(analysisAsyncTaskService).markFailed(
                runningTask.getTaskId(),
                AnalysisAsyncFailureReason.INTERNAL_ERROR,
                "자소서 분석 작업이 처리 제한 시간을 초과했습니다.",
                runningTask.getRetryCount()
        );
    }

    @Test
    @DisplayName("batch가 비어 있으면 추가 처리 없이 종료한다")
    void sweepTimedOutTasksStopsWhenNoTimedOutTaskIdsExist() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(analysisAsyncTaskRepository.findTimedOutPendingTaskIds(
                eq(now.minusSeconds(analysisQueueProperties.getQueueTimeoutSeconds())),
                any(Pageable.class)
        ))
                .thenReturn(List.of());
        when(analysisAsyncTaskRepository.findTimedOutRunningTaskIds(
                eq(now.minusSeconds(analysisQueueProperties.getProcessingTimeoutSeconds())),
                any(Pageable.class)
        ))
                .thenReturn(List.of());

        int expiredCount = analysisAsyncTaskSweepCoordinator.sweepTimedOutTasks();

        assertThat(expiredCount).isZero();
        verify(analysisAsyncTaskRepository, never()).findByIdForUpdate(anyString());
        verify(analysisAsyncTaskService, never()).markFailed(anyString(), any(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }
}
