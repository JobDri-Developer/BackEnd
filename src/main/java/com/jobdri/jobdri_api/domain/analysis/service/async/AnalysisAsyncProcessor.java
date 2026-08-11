package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.infrastructure.async.AnalysisAsyncQueueProcessor;
import org.springframework.stereotype.Service;

@Service
// 분석 비동기 작업을 MQ 메시지로 변환해 워커 실행 경로로 넘기는 서비스다.
public class AnalysisAsyncProcessor extends AnalysisAsyncQueueProcessor {

    public AnalysisAsyncProcessor(AnalysisTaskMessagePublisher analysisTaskMessagePublisher) {
        super(analysisTaskMessagePublisher);
    }
}
