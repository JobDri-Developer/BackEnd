package com.jobdri.jobdri_api.domain.analysis.service.async;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.worker.analysis")
// 분석 worker queue/timeout/retry 설정을 한 곳에 묶어두는 설정 객체다.
public class AnalysisQueueProperties {

    private String exchange = "jobdri.worker.exchange";
    private String queue = "jobdri.analysis.execute";
    private String routingKey = "analysis.execute";
    private String dlq = "jobdri.analysis.execute.dlq";
    private int maxRetryCount = 3;
    private Long queueTimeoutSeconds = 120L;
    private Long processingTimeoutSeconds = 600L;
    private long queueTimeoutMinutes = 10;
    private long processingTimeoutMinutes = 20;

    public long getQueueTimeoutSeconds() {
        return resolveTimeoutSeconds(queueTimeoutSeconds, queueTimeoutMinutes);
    }

    public long getProcessingTimeoutSeconds() {
        return resolveTimeoutSeconds(processingTimeoutSeconds, processingTimeoutMinutes);
    }

    private long resolveTimeoutSeconds(Long timeoutSeconds, long timeoutMinutes) {
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return timeoutSeconds;
        }
        return timeoutMinutes <= 0 ? 0 : timeoutMinutes * 60;
    }
}
