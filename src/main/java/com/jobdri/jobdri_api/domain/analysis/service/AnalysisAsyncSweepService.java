package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.CreditStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisAsyncSweepService {

    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisService analysisService;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisQueueProperties analysisQueueProperties;

    public int sweepTimedOutTasks() {
        int expiredCount = 0;
        for (AnalysisAsyncTask task : analysisAsyncTaskRepository.findByStatusIn(EnumSet.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            try {
                expiredCount += transactionTemplate.execute(status -> sweepTimedOutTask(task.getTaskId()));
            } catch (RuntimeException e) {
                log.error("Analysis async task sweep failed for taskId={}", task.getTaskId(), e);
            }
        }
        return expiredCount;
    }

    private int sweepTimedOutTask(String taskId) {
        AnalysisAsyncTask task = analysisAsyncTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED) {
            return 0;
        }

        ExpirationDecision expirationDecision = resolveExpiration(task);
        if (expirationDecision == null) {
            return 0;
        }

        releaseCreditIfNeeded(task);
        analysisAsyncTaskService.markFailed(
                task.getTaskId(),
                expirationDecision.failureReason(),
                expirationDecision.errorMessage(),
                task.getRetryCount()
        );
        return 1;
    }

    private ExpirationDecision resolveExpiration(AnalysisAsyncTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getStatus() == TaskStatus.PENDING
                && isExpired(task.getSubmittedAt(), now, analysisQueueProperties.getQueueTimeoutSeconds())) {
            return new ExpirationDecision(
                    FailureReason.QUEUE_TIMEOUT,
                    "자소서 분석 작업이 대기열에서 시간 내 처리되지 않았습니다."
            );
        }

        LocalDateTime lastActivityAt = task.getLastAttemptAt() != null ? task.getLastAttemptAt() : task.getStartedAt();
        if (task.getStatus() == TaskStatus.RUNNING
                && isExpired(lastActivityAt, now, analysisQueueProperties.getProcessingTimeoutSeconds())) {
            return new ExpirationDecision(
                    FailureReason.INTERNAL_ERROR,
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

    private void releaseCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != CreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return;
        }

        User user = userService.getUser(task.getUserId());
        analysisService.refundAnalysisCredit(user, task.getCreditReferenceId());
        analysisAsyncTaskService.markCreditReleased(task.getTaskId());
    }

    private record ExpirationDecision(FailureReason failureReason, String errorMessage) {
    }
}
