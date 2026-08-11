package com.jobdri.jobdri_api.domain.analysis.application.usecase.async;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncProcessor;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncTaskService;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

// 분석 비동기 작업의 접수/조회/취소 유스케이스를 조율한다.
public class AnalysisAsyncUseCase {
    private static final String ACTIVE_TASK_UNIQUE_CONSTRAINT = "uk_analysis_async_tasks_active_user_mock_apply";

    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisAsyncProcessor analysisAsyncProcessor;
    private final AnalysisService analysisService;
    private final UserService userService;

    public AnalysisAsyncUseCase(
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncProcessor analysisAsyncProcessor,
            AnalysisService analysisService,
            UserService userService
    ) {
        this.analysisAsyncTaskService = analysisAsyncTaskService;
        this.analysisAsyncProcessor = analysisAsyncProcessor;
        this.analysisService = analysisService;
        this.userService = userService;
    }

    public AnalysisAsyncSubmitResponse submit(User user, Long mockApplyId) {
        User validatedUser = userService.validateUser(user);
        analysisService.validateAnalysisRequest(validatedUser, mockApplyId);

        return analysisAsyncTaskService.findActiveTask(validatedUser.getId(), mockApplyId)
                .map(this::toInProgressResponse)
                .orElseGet(() -> createCachedOrProcessTask(validatedUser, mockApplyId));
    }

    public AnalysisAsyncStatusResponse getTask(User user, Long mockApplyId, String taskId) {
        User validatedUser = userService.validateUser(user);
        AnalysisAsyncStatusResponse status = analysisAsyncTaskService.getTaskStatus(validatedUser.getId(), taskId);
        if (!status.mockApplyId().equals(mockApplyId)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "요청한 mockApplyId와 작업 정보가 일치하지 않습니다.");
        }
        if (!"SUCCEEDED".equals(status.status())) {
            return status;
        }

        return AnalysisAsyncStatusResponse.builder()
                .taskId(status.taskId())
                .mockApplyId(status.mockApplyId())
                .status(status.status())
                .message(status.message())
                .error(status.error())
                .failureReason(status.failureReason())
                .workerId(status.workerId())
                .retryCount(status.retryCount())
                .maxRetryCount(status.maxRetryCount())
                .queueLatencyMillis(status.queueLatencyMillis())
                .createdAt(status.createdAt())
                .submittedAt(status.submittedAt())
                .lastAttemptAt(status.lastAttemptAt())
                .startedAt(status.startedAt())
                .completedAt(status.completedAt())
                .cancelRequested(status.cancelRequested())
                .cancelledAt(status.cancelledAt())
                .currentStep(status.currentStep())
                .progressPercent(status.progressPercent())
                .estimatedRemainingSeconds(status.estimatedRemainingSeconds())
                .steps(status.steps())
                .result(analysisService.getAnalysis(validatedUser, status.mockApplyId()))
                .build();
    }

    public AnalysisAsyncCancelResponse cancel(User user, Long mockApplyId, String taskId) {
        User validatedUser = userService.validateUser(user);
        return analysisAsyncTaskService.cancelTask(validatedUser.getId(), mockApplyId, taskId);
    }

    private AnalysisAsyncSubmitResponse createCachedOrProcessTask(User user, Long mockApplyId) {
        if (analysisService.hasReusableAnalysis(user, mockApplyId)) {
            return toCachedResponse();
        }
        return createAndProcessTask(user, mockApplyId);
    }

    private AnalysisAsyncSubmitResponse createAndProcessTask(User user, Long mockApplyId) {
        PendingTaskResult pendingTaskResult = createPendingTask(user, mockApplyId);
        if (!pendingTaskResult.created()) {
            return toInProgressResponse(pendingTaskResult.task());
        }

        AnalysisAsyncTask task = pendingTaskResult.task();
        String taskId = task.getTaskId();

        try {
            analysisAsyncProcessor.process(
                    taskId,
                    user.getId(),
                    mockApplyId,
                    task.getMaxRetryCount()
            );
            return new AnalysisAsyncSubmitResponse(
                    taskId,
                    "PENDING",
                    "자소서 분석 비동기 작업이 접수되었습니다.",
                    false,
                    false
            );
        } catch (RuntimeException e) {
            analysisAsyncTaskService.deleteTask(taskId);
            throw e;
        }
    }

    private PendingTaskResult createPendingTask(User user, Long mockApplyId) {
        try {
            return new PendingTaskResult(
                    analysisAsyncTaskService.createPendingTask(user.getId(), mockApplyId),
                    true
            );
        } catch (DataIntegrityViolationException e) {
            if (!isActiveTaskUniqueConflict(e)) {
                throw e;
            }
            AnalysisAsyncTask existingTask = analysisAsyncTaskService.findActiveTask(user.getId(), mockApplyId)
                    .orElseThrow(() -> e);
            return new PendingTaskResult(existingTask, false);
        }
    }

    private AnalysisAsyncSubmitResponse toInProgressResponse(AnalysisAsyncTask task) {
        return new AnalysisAsyncSubmitResponse(
                task.getTaskId(),
                task.getStatus().name(),
                "이미 진행 중인 자소서 분석 작업이 있습니다.",
                false,
                false
        );
    }

    private AnalysisAsyncSubmitResponse toCachedResponse() {
        return new AnalysisAsyncSubmitResponse(
                null,
                "SUCCEEDED",
                "동일 입력의 기존 자소서 분석 결과를 재사용했습니다.",
                true,
                true
        );
    }

    private boolean isActiveTaskUniqueConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && containsConstraintName(constraintViolation.getConstraintName())) {
                return true;
            }
            if (containsConstraintName(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean containsConstraintName(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(ACTIVE_TASK_UNIQUE_CONSTRAINT);
    }

    private record PendingTaskResult(AnalysisAsyncTask task, boolean created) {
    }
}
