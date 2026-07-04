package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisAsyncProcessor {

    private final AnalysisAsyncTaskService analysisAsyncTaskService;
    private final AnalysisService analysisService;
    private final UserService userService;

    @Async("llmAsyncExecutor")
    public void process(String taskId, Long userId, Long mockApplyId, String creditReferenceId) {
        analysisAsyncTaskService.markRunning(taskId);

        try {
            User user = userService.getUser(userId);
            AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(user, mockApplyId);
            var llmResponse = analysisService.executeAnalysis(payload);
            analysisService.finalizeAnalysis(user, mockApplyId, payload, llmResponse);
            analysisAsyncTaskService.markSuccess(taskId);
        } catch (Exception e) {
            log.error("자소서 분석 비동기 처리 실패: taskId={}, mockApplyId={}", taskId, mockApplyId, e);
            try {
                analysisService.refundAnalysisCredit(userService.getUser(userId), creditReferenceId);
            } catch (Exception refundException) {
                log.error("자소서 분석 실패 환불 처리 실패: taskId={}, userId={}", taskId, userId, refundException);
            }
            analysisAsyncTaskService.markFailed(taskId, e.getMessage());
        }
    }
}
