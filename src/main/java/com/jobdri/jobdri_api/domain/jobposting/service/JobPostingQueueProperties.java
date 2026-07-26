package com.jobdri.jobdri_api.domain.jobposting.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.worker.job-posting")
public class JobPostingQueueProperties {

    private String exchange = "jobdri.worker.exchange";
    private String queue = "jobdri.job-posting.ingest";
    private String routingKey = "job-posting.ingest";
    private String dlq = "jobdri.job-posting.ingest.dlq";
    private int maxRetryCount = 3;
    private Long queueTimeoutSeconds = 30L;
    private Long processingTimeoutSeconds = 60L;
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
