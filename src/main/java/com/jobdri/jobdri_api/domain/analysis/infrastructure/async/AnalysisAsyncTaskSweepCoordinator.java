package com.jobdri.jobdri_api.domain.analysis.infrastructure.async;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncTaskStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncCreditCoordinator;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncTaskService;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisQueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
// timeout/retry 기준으로 만료된 분석 async task를 정리한다.
public class AnalysisAsyncTaskSweepCoordinator {
    private static final int SWEEP_BATCH_SIZE = 100;

    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisAsyncCreditCoordinator analysisAsyncCreditCoordinator;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisQueueProperties analysisQueueProperties;
    private final Clock clock;

    public AnalysisAsyncTaskSweepCoordinator(
            AnalysisAsyncTaskRepository analysisAsyncTaskRepository,
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncCreditCoordinator analysisAsyncCreditCoordinator,
            TransactionTemplate transactionTemplate,
            AnalysisQueueProperties analysisQueueProperties,
            Clock clock
    ) {
        this.analysisAsyncTaskRepository = analysisAsyncTaskRepository;
        this.analysisAsyncTaskService = analysisAsyncTaskService;
        this.analysisAsyncCreditCoordinator = analysisAsyncCreditCoordinator;
        this.transactionTemplate = transactionTemplate;
        this.analysisQueueProperties = analysisQueueProperties;
        this.clock = clock;
    }

    public int sweepTimedOutTasks() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expiredCount = sweepTimedOutPendingTasks(now);
        expiredCount += sweepTimedOutRunningTasks(now);
        return expiredCount;
    }

    private int sweepTimedOutPendingTasks(LocalDateTime now) {
        LocalDateTime deadline = now.minusSeconds(analysisQueueProperties.getQueueTimeoutSeconds());
        return sweepTimedOutTaskIds(
                () -> analysisAsyncTaskRepository.findTimedOutPendingTaskIds(
                        deadline,
                        PageRequest.of(0, SWEEP_BATCH_SIZE)
                )
        );
    }

    private int sweepTimedOutRunningTasks(LocalDateTime now) {
        LocalDateTime deadline = now.minusSeconds(analysisQueueProperties.getProcessingTimeoutSeconds());
        return sweepTimedOutTaskIds(
                () -> analysisAsyncTaskRepository.findTimedOutRunningTaskIds(
                        deadline,
                        PageRequest.of(0, SWEEP_BATCH_SIZE)
                )
        );
    }

    private int sweepTimedOutTaskIds(TaskIdBatchLoader taskIdBatchLoader) {
        int expiredCount = 0;
        while (true) {
            List<String> taskIds = taskIdBatchLoader.load();
            if (taskIds.isEmpty()) {
                return expiredCount;
            }
            for (String taskId : taskIds) {
                expiredCount += sweepTimedOutTask(taskId);
            }
            if (taskIds.size() < SWEEP_BATCH_SIZE) {
                return expiredCount;
            }
        }
    }

    private int sweepTimedOutTask(String taskId) {
        try {
            return transactionTemplate.execute(status -> sweepTimedOutTaskInTransaction(taskId));
        } catch (RuntimeException e) {
            log.error("Analysis async task sweep failed for taskId={}", taskId, e);
            return 0;
        }
    }

    private int sweepTimedOutTaskInTransaction(String taskId) {
        AnalysisAsyncTask task = analysisAsyncTaskRepository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() == AnalysisAsyncTaskStatus.SUCCEEDED || task.getStatus() == AnalysisAsyncTaskStatus.FAILED) {
            return 0;
        }

        ExpirationDecision expirationDecision = resolveExpiration(task);
        if (expirationDecision == null) {
            return 0;
        }

        analysisAsyncCreditCoordinator.releaseReservedCreditIfNeeded(task);
        analysisAsyncTaskService.markFailed(
                task.getTaskId(),
                expirationDecision.failureReason(),
                expirationDecision.errorMessage(),
                task.getRetryCount()
        );
        return 1;
    }

    private ExpirationDecision resolveExpiration(AnalysisAsyncTask task) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (task.getStatus() == AnalysisAsyncTaskStatus.PENDING
                && isExpired(task.getSubmittedAt(), now, analysisQueueProperties.getQueueTimeoutSeconds())) {
            return new ExpirationDecision(
                    AnalysisAsyncFailureReason.QUEUE_TIMEOUT,
                    "자소서 분석 작업이 대기열에서 시간 내 처리되지 않았습니다."
            );
        }

        LocalDateTime lastActivityAt = task.getLastAttemptAt() != null ? task.getLastAttemptAt() : task.getStartedAt();
        if (task.getStatus() == AnalysisAsyncTaskStatus.RUNNING
                && isExpired(lastActivityAt, now, analysisQueueProperties.getProcessingTimeoutSeconds())) {
            return new ExpirationDecision(
                    AnalysisAsyncFailureReason.INTERNAL_ERROR,
                    "자소서 분석 작업이 처리 제한 시간을 초과했습니다."
            );
        }

        return null;
    }

    private boolean isExpired(LocalDateTime baseTime, LocalDateTime now, long timeoutSeconds) {
        if (baseTime == null || timeoutSeconds <= 0) {
            return false;
        }
        return Duration.between(baseTime, now).getSeconds() >= timeoutSeconds;
    }

    private record ExpirationDecision(AnalysisAsyncFailureReason failureReason, String errorMessage) {
    }

    @FunctionalInterface
    private interface TaskIdBatchLoader {
        List<String> load();
    }
}
