package com.jobdri.jobdri_api.domain.jobposting.service;

final class MockQuestionCachePropertiesTestSupport {

    static final String PROMPT_VERSION = "v1";

    private MockQuestionCachePropertiesTestSupport() {
    }

    static MockQuestionCacheProperties createProperties() {
        MockQuestionCacheProperties properties = new MockQuestionCacheProperties();
        properties.setPromptVersion(PROMPT_VERSION);
        return properties;
    }
}
