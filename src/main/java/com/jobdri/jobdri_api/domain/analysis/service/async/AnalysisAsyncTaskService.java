package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisProgressStepResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncCreditStatus;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncFailureReason;
import com.jobdri.jobdri_api.domain.analysis.type.AnalysisAsyncTaskStatus;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationTargetType;
import com.jobdri.jobdri_api.domain.notification.entity.NotificationType;
import com.jobdri.jobdri_api.domain.notification.service.NotificationService;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator.AsyncTaskProgressStatus;
import com.jobdri.jobdri_api.global.async.AsyncProgressCalculator.ProgressStepDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
// 분석 비동기 task 엔티티의 생성, 상태 전이, 조회를 전담하는 서비스다.
public class AnalysisAsyncTaskService {
    private static final int DEFAULT_ESTIMATED_REMAINING_SECONDS = 180;
    private static final List<ProgressStepDefinition> PROGRESS_STEPS = List.of(
            new ProgressStepDefinition("VALIDATING_INPUT", "분석할 내용을 확인하고 있어요"),
            new ProgressStepDefinition("PREPARING_CONTEXT", "공고와 자소서를 준비하고 있어요"),
            new ProgressStepDefinition("CALLING_LLM", "자기소개서를 평가하고 있어요"),
            new ProgressStepDefinition("VALIDATING_RESULT", "분석 결과를 검증하고 있어요"),
            new ProgressStepDefinition("SAVING_RESULT", "분석 결과를 저장하고 있어요"),
            new ProgressStepDefinition("COMPLETED", "분석이 완료되었습니다")
    );

    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    private final AnalysisAsyncSseService analysisAsyncSseService;
    private final NotificationService notificationService;
    private final AsyncMetricsRecorder asyncMetricsRecorder;
    private final AnalysisQueueProperties analysisQueueProperties;
    private final AnalysisCreditService analysisCreditService;
    private final UserService userService;
    private final AsyncProgressCalculator asyncProgressCalculator;

    @Transactional
    public AnalysisAsyncTask createPendingTask(Long userId, Long mockApplyId) {
        return analysisAsyncTaskRepository.saveAndFlush(
                AnalysisAsyncTask.pending(userId, mockApplyId, analysisQueueProperties.getMaxRetryCount())
        );
    }

    @Transactional
    public void deleteTask(String taskId) {
        analysisAsyncTaskRepository.deleteById(taskId);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisAsyncTask> findActiveTask(Long userId, Long mockApplyId) {
        return analysisAsyncTaskRepository.findFirstByUserIdAndMockApplyIdAndStatusInOrderByCreatedAtDesc(
                userId,
                mockApplyId,
                EnumSet.of(AnalysisAsyncTaskStatus.PENDING, AnalysisAsyncTaskStatus.RUNNING)
        );
    }

    @Transactional
    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        AnalysisAsyncTask task = getTask(taskId);
        task.markRunning(workerId, retryCount, submittedAt);
        if (task.getQueueLatencyMillis() != null) {
            asyncMetricsRecorder.recordQueueWait("analysis", task.getQueueLatencyMillis());
        }
        publishAfterCommit(toStatusResponse(task));
    }

    @Transactional
    public void markSuccess(String taskId, AnalysisResponse result) {
        AnalysisAsyncTask task = getTask(taskId);
        if (task.getStatus() == AnalysisAsyncTaskStatus.SUCCEEDED) {
            return;
        }
        if (task.getStatus() == AnalysisAsyncTaskStatus.CANCELLED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "취소된 자소서 분석 비동기 작업입니다. taskId=" + taskId
            );
        }
        if (task.getStatus() == AnalysisAsyncTaskStatus.FAILED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 실패 처리된 자소서 분석 비동기 작업입니다. taskId=" + taskId
            );
        }
        task.markSuccess();
        recordProcessingMetric(task, "succeeded");
        publishAfterCommit(toStatusResponse(task, result));
        createSuccessNotificationSafely(task);
    }

    @Transactional
    public void markRetryScheduled(String taskId, AnalysisAsyncFailureReason failureReason, String errorMessage, int retryCount) {
        AnalysisAsyncTask task = getTask(taskId);
        recordProcessingMetric(task, "retry");
        task.markRetryScheduled(failureReason, errorMessage, retryCount);
        publishAfterCommit(toStatusResponse(task));
    }

    @Transactional
    public void markFailed(String taskId, AnalysisAsyncFailureReason failureReason, String errorMessage, int retryCount) {
        AnalysisAsyncTask task = getTask(taskId);
        recordProcessingMetric(task, "failed");
        task.markFailed(failureReason, errorMessage, retryCount);
        publishAfterCommit(toStatusResponse(task));
        createFailureNotificationSafely(task);
    }

    @Transactional
    public AnalysisAsyncCancelResponse cancelTask(Long userId, Long mockApplyId, String taskId) {
        AnalysisAsyncTask task = analysisAsyncTaskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
        if (!task.getMockApplyId().equals(mockApplyId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "요청한 mockApplyId와 작업 정보가 일치하지 않습니다.");
        }

        AnalysisAsyncTaskStatus previousStatus = task.getStatus();
        LocalDateTime previousCancelledAt = task.getCancelledAt();
        task.requestCancel();
        boolean cancelled = task.getStatus() == AnalysisAsyncTaskStatus.CANCELLED;
        boolean newlyCancelled = previousStatus != AnalysisAsyncTaskStatus.CANCELLED && cancelled;
        if (newlyCancelled) {
            releaseCreditIfNeeded(task);
            recordProcessingMetric(task, "cancelled");
        }
        if (newlyCancelled || previousCancelledAt == null && cancelled) {
            publishAfterCommit(toStatusResponse(task));
        }
        return new AnalysisAsyncCancelResponse(
                task.getTaskId(),
                task.getStatus().name(),
                task.getMessage()
        );
    }

    @Transactional
    public void updateWorkerMetadata(String taskId, String workerId, Long queueLatencyMillis) {
        getTask(taskId).updateWorkerMetadata(workerId, queueLatencyMillis);
    }

    @Transactional
    public void markCreditReserved(String taskId, String creditReferenceId) {
        getTask(taskId).markCreditReserved(creditReferenceId);
    }

    @Transactional
    public void markCreditConfirmed(String taskId) {
        getTask(taskId).markCreditConfirmed();
    }

    @Transactional
    public void markCreditReleased(String taskId) {
        getTask(taskId).markCreditReleased();
    }

    @Transactional(readOnly = true)
    public AnalysisAsyncCreditStatus getCreditStatus(String taskId) {
        return getTask(taskId).getCreditStatus();
    }

    @Transactional(readOnly = true)
    public AnalysisAsyncStatusResponse getTaskStatus(Long userId, String taskId) {
        AnalysisAsyncTask task = analysisAsyncTaskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
        return toStatusResponse(task);
    }

    @Transactional(readOnly = true)
    public AnalysisAsyncStatusResponse getTaskStatusByTaskId(String taskId) {
        return toStatusResponse(getTask(taskId));
    }

    private AnalysisAsyncTask getTask(String taskId) {
        return analysisAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private void recordProcessingMetric(AnalysisAsyncTask task, String outcome) {
        if (task.getStartedAt() == null) {
            return;
        }
        long durationMillis = Math.max(0L, Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis());
        asyncMetricsRecorder.recordProcessing("analysis", outcome, durationMillis);
    }

    private AnalysisAsyncStatusResponse toStatusResponse(AnalysisAsyncTask task) {
        return toStatusResponse(task, null);
    }

    private AnalysisAsyncStatusResponse toStatusResponse(AnalysisAsyncTask task, AnalysisResponse result) {
        return AnalysisAsyncStatusResponse.builder()
                .taskId(task.getTaskId())
                .mockApplyId(task.getMockApplyId())
                .status(task.getStatus().name())
                .message(task.getMessage())
                .error(task.getError())
                .failureReason(task.getFailureReason() != null ? task.getFailureReason().name() : null)
                .workerId(task.getWorkerId())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .queueLatencyMillis(task.getQueueLatencyMillis())
                .createdAt(task.getCreatedAt())
                .submittedAt(task.getSubmittedAt())
                .lastAttemptAt(task.getLastAttemptAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .cancelRequested(task.isCancelRequested())
                .cancelledAt(task.getCancelledAt())
                .currentStep(resolveCurrentStep(task))
                .progressPercent(asyncProgressCalculator.resolveProgressPercent(toProgressStatus(task.getStatus()), task.getProgressPercent()))
                .estimatedRemainingSeconds(asyncProgressCalculator.resolveEstimatedRemainingSeconds(
                        toProgressStatus(task.getStatus()),
                        task.getEstimatedRemainingSeconds(),
                        task.getStartedAt(),
                        DEFAULT_ESTIMATED_REMAINING_SECONDS
                ))
                .steps(buildSteps(task))
                .result(task.getStatus() == AnalysisAsyncTaskStatus.SUCCEEDED ? result : null)
                .build();
    }

    private void releaseCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != AnalysisAsyncCreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return;
        }
        User user = userService.getUser(task.getUserId());
        analysisCreditService.refund(user, task.getCreditReferenceId());
        task.markCreditReleased();
    }

    private String resolveCurrentStep(AnalysisAsyncTask task) {
        return asyncProgressCalculator.resolveCurrentStep(
                toProgressStatus(task.getStatus()),
                task.getCurrentStep(),
                "VALIDATING_INPUT"
        );
    }

    private List<AnalysisProgressStepResponse> buildSteps(AnalysisAsyncTask task) {
        return asyncProgressCalculator.buildSteps(
                toProgressStatus(task.getStatus()),
                resolveCurrentStep(task),
                PROGRESS_STEPS,
                step -> new AnalysisProgressStepResponse(step.code(), step.label(), step.status())
        );
    }

    private AsyncTaskProgressStatus toProgressStatus(AnalysisAsyncTaskStatus status) {
        return AsyncTaskProgressStatus.valueOf(status.name());
    }

    private void publishAfterCommit(AnalysisAsyncStatusResponse statusResponse) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            analysisAsyncSseService.publish(statusResponse);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                analysisAsyncSseService.publish(statusResponse);
            }
        });
    }

    private void createSuccessNotificationSafely(AnalysisAsyncTask task) {
        try {
            createSuccessNotification(task);
        } catch (Exception e) {
            log.warn("분석 완료 알림 생성에 실패했습니다. taskId={}, userId={}", task.getTaskId(), task.getUserId(), e);
        }
    }

    private void createFailureNotificationSafely(AnalysisAsyncTask task) {
        try {
            createFailureNotification(task);
        } catch (Exception e) {
            log.warn(
                    "분석 실패 알림 생성에 실패했습니다. taskId={}, userId={}, error={}",
                    task.getTaskId(),
                    task.getUserId(),
                    task.getError(),
                    e
            );
        }
    }

    private void createSuccessNotification(AnalysisAsyncTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("mockApplyId", task.getMockApplyId());
        payload.put("status", task.getStatus().name());

        notificationService.createNotification(
                task.getUserId(),
                NotificationType.ANALYSIS_ASYNC_SUCCEEDED,
                "자소서 분석이 완료되었습니다.",
                "분석 결과를 확인해보세요.",
                NotificationTargetType.ANALYSIS_RESULT,
                String.valueOf(task.getMockApplyId()),
                payload
        );
    }

    private void createFailureNotification(AnalysisAsyncTask task) {
        String userFacingMessage = "자소서 분석 처리 중 오류가 발생했습니다.";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("mockApplyId", task.getMockApplyId());
        payload.put("failureReason", task.getFailureReason() != null ? task.getFailureReason().name() : null);
        payload.put("status", task.getStatus().name());

        notificationService.createNotification(
                task.getUserId(),
                NotificationType.ANALYSIS_ASYNC_FAILED,
                "자소서 분석이 실패했습니다.",
                userFacingMessage,
                NotificationTargetType.ANALYSIS_TASK,
                task.getTaskId(),
                payload
        );
    }

}
