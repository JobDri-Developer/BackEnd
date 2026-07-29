package com.jobdri.jobdri_api.domain.jobposting.service;

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
                "gpt-4o-mini",
                "embed-v4.0",
                3,
                5
        );
    }
}
