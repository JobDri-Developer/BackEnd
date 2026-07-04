package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerCompleteRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisWorkerContextResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.CreditStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.FailureReason;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask.TaskStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisWorkerBridgeService {

    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisAsyncTaskRepository analysisAsyncTaskRepository;
    private final AnalysisService analysisService;
    private final UserService userService;

    public void markRunning(String taskId, String workerId, int retryCount, Instant submittedAt) {
        analysisAsyncTaskService.markRunning(taskId, workerId, retryCount, submittedAt);
    }

    public void markRetry(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        analysisAsyncTaskService.markRetryScheduled(taskId, failureReason, errorMessage, retryCount);
    }

    @Transactional
    public void failTask(String taskId, FailureReason failureReason, String errorMessage, int retryCount) {
        AnalysisAsyncTask task = getTask(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED) {
            return;
        }

        releaseCreditIfNeeded(task);
        analysisAsyncTaskService.markFailed(taskId, failureReason, errorMessage, retryCount);
    }

    @Transactional(readOnly = true)
    public AnalysisWorkerContextResponse getContext(String taskId, Long userId, Long mockApplyId) {
        AnalysisAsyncTask task = getTask(taskId);
        if (!task.getUserId().equals(userId) || !task.getMockApplyId().equals(mockApplyId)) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "자소서 분석 worker 컨텍스트 요청 정보가 작업 정보와 일치하지 않습니다."
            );
        }
        User user = userService.getUser(userId);
        AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(user, mockApplyId);

        return new AnalysisWorkerContextResponse(
                userId,
                mockApplyId,
                payload.jobPosting().getCompany().getName(),
                payload.jobPosting().getDetailClassification().getDetailName(),
                payload.jobPosting().getTask(),
                payload.jobPosting().getRequirement(),
                payload.jobPosting().getPreferred(),
                payload.jobPosting().getDetailClassification().getMiddleClassification().getClassification().getBigName(),
                payload.jobPosting().getDetailClassification().getMiddleClassification().getMiddleName(),
                payload.jobPosting().getDetailClassification().getDetailName(),
                toQuestionItems(payload.questions())
        );
    }

    @Transactional
    public AnalysisResponse completeTask(String taskId, AnalysisWorkerCompleteRequest request) {
        AnalysisAsyncTask task = getTask(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return analysisService.getAnalysis(userService.getUser(request.userId()), request.mockApplyId());
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 실패 처리된 자소서 분석 비동기 작업입니다. taskId=" + taskId
            );
        }

        User user = userService.getUser(request.userId());
        AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(user, request.mockApplyId());
        AnalysisLlmResponse llmResponse = request.llmResponse();
        AnalysisResponse response = analysisService.finalizeAnalysis(user, request.mockApplyId(), payload, llmResponse);
        analysisService.confirmAnalysisCredit(user, task.getCreditReferenceId());
        analysisAsyncTaskService.markCreditConfirmed(taskId);
        analysisAsyncTaskService.markSuccess(taskId);
        return response;
    }

    private List<AnalysisWorkerContextResponse.AnalysisWorkerQuestionItem> toQuestionItems(List<Question> questions) {
        return questions.stream()
                .map(question -> new AnalysisWorkerContextResponse.AnalysisWorkerQuestionItem(
                        question.getId(),
                        question.getContent(),
                        question.getAnswer(),
                        question.getLimit()
                ))
                .toList();
    }

    private AnalysisAsyncTask getTask(String taskId) {
        return analysisAsyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_ASYNC_TASK_NOT_FOUND,
                        "해당 자소서 분석 비동기 작업을 찾을 수 없습니다. taskId=" + taskId
                ));
    }

    private void releaseCreditIfNeeded(AnalysisAsyncTask task) {
        if (task.getCreditStatus() != CreditStatus.RESERVED || task.getCreditReferenceId() == null) {
            return;
        }
        User user = userService.getUser(task.getUserId());
        analysisService.releaseAnalysisCredit(user, task.getCreditReferenceId());
        analysisAsyncTaskService.markCreditReleased(task.getTaskId());
    }
}
