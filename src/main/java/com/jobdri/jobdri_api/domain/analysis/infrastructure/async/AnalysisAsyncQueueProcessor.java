package com.jobdri.jobdri_api.domain.analysis.infrastructure.async;

import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisTaskMessage;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisTaskMessagePublisher;

// 분석 비동기 작업을 MQ 워커 메시지로 변환해 발행한다.
public class AnalysisAsyncQueueProcessor {
    private final AnalysisTaskMessagePublisher analysisTaskMessagePublisher;

    public AnalysisAsyncQueueProcessor(AnalysisTaskMessagePublisher analysisTaskMessagePublisher) {
        this.analysisTaskMessagePublisher = analysisTaskMessagePublisher;
    }

    public void process(String taskId, Long userId, Long mockApplyId, int maxRetryCount) {
        analysisTaskMessagePublisher.publish(
                AnalysisTaskMessage.of(taskId, userId, mockApplyId, maxRetryCount)
        );
    }
}
