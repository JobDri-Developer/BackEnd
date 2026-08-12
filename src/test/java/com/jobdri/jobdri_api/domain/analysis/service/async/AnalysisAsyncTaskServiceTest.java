package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.notification.service.NotificationService;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisAsyncTaskServiceTest {

    @Mock
    private AnalysisAsyncTaskRepository analysisAsyncTaskRepository;

    @Mock
    private AnalysisAsyncSseService analysisAsyncSseService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AsyncMetricsRecorder asyncMetricsRecorder;

    @Mock
    private AnalysisAsyncCreditCoordinator analysisAsyncCreditCoordinator;

    @Test
    @DisplayName("동시에 재접수해도 PUBLISH_FAILED task는 한 번만 reopen 된다")
    void reopenPublishFailureTaskIsAtomicAcrossConcurrentRequests() throws Exception {
        AnalysisQueueProperties queueProperties = new AnalysisQueueProperties();
        AnalysisAsyncTaskService analysisAsyncTaskService = new AnalysisAsyncTaskService(
                analysisAsyncTaskRepository,
                analysisAsyncSseService,
                notificationService,
                asyncMetricsRecorder,
                queueProperties,
                analysisAsyncCreditCoordinator,
                new AsyncProgressCalculator()
        );
        AnalysisAsyncTask failedTask = spy(AnalysisAsyncTask.pending(1L, 10L, 3));
        failedTask.markFailed(AnalysisAsyncFailureReason.PUBLISH_FAILED, "publish failed", 0);

        CountDownLatch firstReopened = new CountDownLatch(1);
        AtomicBoolean firstLookup = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            firstReopened.countDown();
            return null;
        }).when(failedTask).reopenForRepublish();
        when(analysisAsyncTaskRepository.findByIdForUpdate(failedTask.getTaskId())).thenAnswer(invocation -> {
            if (firstLookup.getAndSet(false)) {
                return Optional.of(failedTask);
            }
            assertThat(firstReopened.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(failedTask);
        });

        List<AnalysisAsyncTaskService.ReopenPublishFailureResult> results = runConcurrently(2, () ->
                analysisAsyncTaskService.reopenPublishFailureTask(failedTask.getTaskId())
        );

        assertThat(results).hasSize(2);
        assertThat(results).extracting(AnalysisAsyncTaskService.ReopenPublishFailureResult::reopened)
                .containsExactlyInAnyOrder(true, false);
        assertThat(failedTask.getStatus().name()).isEqualTo("PENDING");
        verify(analysisAsyncSseService, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    private <T> List<T> runConcurrently(int threadCount, Callable<T> task) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return task.call();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>(threadCount);
            for (Future<T> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
