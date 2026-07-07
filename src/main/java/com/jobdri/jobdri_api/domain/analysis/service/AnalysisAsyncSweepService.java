package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.CreditStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncSweepService {

    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisService analysisService;
    private final UserService userService;

    @Value("${app.worker.analysis.queue-timeout-minutes:10}")
    private long queueTimeoutMinutes;

    @Value("${app.worker.analysis.processing-timeout-minutes:20}")
    private long processingTimeoutMinutes;

    @Transactional
    public int sweepTimedOutTasks() {
        int expiredCount = 0;
        for (AnalysisAsyncTask task : analysisAsyncTaskRepository.findByStatusIn(EnumSet.of(TaskStatus.PENDING, TaskStatus.RUNNING))) {
            ExpirationDecision expirationDecision = resolveExpiration(task);
            if (expirationDecision == null) {
                continue;
            }

            releaseCreditIfNeeded(task);
            analysisAsyncTaskService.markFailed(
                    task.getTaskId(),
                    expirationDecision.failureReason(),
                    expirationDecision.errorMessage(),
                    task.getRetryCount()
            );
            expiredCount++;
        }
        return expiredCount;
    }

    private ExpirationDecision resolveExpiration(AnalysisAsyncTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getStatus() == TaskStatus.PENDING && isExpired(task.getSubmittedAt(), now, queueTimeoutMinutes)) {
            return new ExpirationDecision(
                    FailureReason.QUEUE_TIMEOUT,
                    "자소서 분석 작업이 대기열에서 시간 내 처리되지 않았습니다."
            );
        }

        LocalDateTime lastActivityAt = task.getLastAttemptAt() != null ? task.getLastAttemptAt() : task.getStartedAt();
        if (task.getStatus() == TaskStatus.RUNNING && isExpired(lastActivityAt, now, processingTimeoutMinutes)) {
            return new ExpirationDecision(
                    FailureReason.INTERNAL_ERROR,
                    "자소서 분석 작업이 처리 제한 시간을 초과했습니다."
            );
        }

        return null;
    }

    private boolean isExpired(LocalDateTime baseTime, LocalDateTime now, long timeoutMinutes) {
        if (baseTime == null || timeoutMinutes <= 0) {
            return false;
        }
        return Duration.between(baseTime, now).toMinutes() >= timeoutMinutes;
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
