package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.AnalysisTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 분석 비동기 작업을 MQ 메시지로 변환해 워커 실행 경로로 넘기는 서비스다.
public class AnalysisAsyncProcessor {

    private final AnalysisTaskMessagePublisher analysisTaskMessagePublisher;

    public void process(String taskId, Long userId, Long mockApplyId, int maxRetryCount) {
        analysisTaskMessagePublisher.publish(
                AnalysisTaskMessage.of(taskId, userId, mockApplyId, maxRetryCount)
        );
    }
}
