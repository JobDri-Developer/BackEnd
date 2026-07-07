package com.jobdri.jobdri_api.domain.analysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// 분석 워커 큐 관련 설정값을 주입받아 묶어두는 설정 객체다.
public record AnalysisQueueProperties(
        @Value("${app.worker.analysis.routing-key:analysis.execute}") String routingKey
) {
}
