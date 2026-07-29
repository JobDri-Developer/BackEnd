package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.cohere.CohereProperties;

final class MockQuestionCachePropertiesTestSupport {

    static final String VERSION_PREFIX = "v1";

    private MockQuestionCachePropertiesTestSupport() {
    }

    static MockQuestionCacheProperties createProperties() {
        MockQuestionCacheProperties properties = new MockQuestionCacheProperties();
        properties.setVersionPrefix(VERSION_PREFIX);
        properties.setLockTtlMillis(30_000L);
        properties.setWaitTimeoutMillis(10_000L);
        properties.setPollIntervalMillis(0L);
        properties.setWaitExecutorPoolSize(1);
        properties.setWaitExecutorQueueCapacity(8);
        return properties;
    }

    static MockQuestionCacheVersionProvider createVersionProvider() {
        return new MockQuestionCacheVersionProvider(
                createProperties(),
                new CohereProperties(
                        "test-api-key",
                        "https://api.cohere.com",
                        new CohereProperties.Embedding("embed-v4.0", 1024, null, null)
                ),
                "gpt-4o-mini",
                3,
                5
        );
    }
}
