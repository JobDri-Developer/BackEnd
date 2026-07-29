package com.jobdri.jobdri_api.domain.jobposting.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.job-posting.mock-question-cache")
public class MockQuestionCacheProperties {

    private String versionPrefix = "v1";
    private long lockTtlMillis = 30_000L;
    private long waitTimeoutMillis = 10_000L;
    private long pollIntervalMillis = 200L;
    private int waitExecutorPoolSize = 4;
    private int waitExecutorQueueCapacity = 64;
}
