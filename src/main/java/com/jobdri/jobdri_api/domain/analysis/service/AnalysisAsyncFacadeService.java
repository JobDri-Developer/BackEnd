package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AnalysisAsyncFacadeService {
    private static final String ACTIVE_TASK_UNIQUE_CONSTRAINT = "uk_analysis_async_tasks_active_user_mock_apply";

    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisAsyncProcessor analysisAsyncProcessor;
    private final AnalysisService analysisService;
    private final UserService userService;

    public AnalysisAsyncSubmitResponse submit(User user, Long mockApplyId) {
        User validatedUser = userService.validateUser(user);
        analysisService.validateAnalysisRequest(validatedUser, mockApplyId);

        return analysisAsyncTaskService.findActiveTask(validatedUser.getId(), mockApplyId)
                .map(this::toInProgressResponse)
                .orElseGet(() -> createAndProcessTask(validatedUser, mockApplyId));
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
                .createdAt(status.createdAt())
                .startedAt(status.startedAt())
                .completedAt(status.completedAt())
                .result(analysisService.getAnalysis(validatedUser, status.mockApplyId()))
                .build();
    }

    private AnalysisAsyncSubmitResponse createAndProcessTask(User user, Long mockApplyId) {
        PendingTaskResult pendingTaskResult = createPendingTask(user, mockApplyId);
        if (!pendingTaskResult.created()) {
            return toInProgressResponse(pendingTaskResult.task());
        }

        AnalysisAsyncTask task = pendingTaskResult.task();
        String taskId = task.getTaskId();
        String creditReferenceId = "analysisTaskId=" + taskId;

        try {
            analysisService.reserveAnalysisCredit(user, creditReferenceId);
            analysisAsyncTaskService.markCreditReserved(taskId, creditReferenceId);
            analysisAsyncProcessor.process(taskId, user.getId(), mockApplyId, creditReferenceId);
            return new AnalysisAsyncSubmitResponse(
                    taskId,
                    "PENDING",
                    "자소서 분석 비동기 작업이 접수되었습니다."
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
                "이미 진행 중인 자소서 분석 작업이 있습니다."
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
