package com.jobdri.jobdri_api.domain.jobposting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockQuestionCacheVersionProviderTest {

    @Test
    @DisplayName("캐시 버전은 수동 prefix와 자동 fingerprint를 함께 포함한다")
    void currentVersionIncludesManualPrefixAndAutomaticFingerprint() {
        MockQuestionCacheVersionProvider versionProvider = MockQuestionCachePropertiesTestSupport.createVersionProvider();

        String version = versionProvider.currentVersion();

        assertThat(version).startsWith(MockQuestionCachePropertiesTestSupport.VERSION_PREFIX + ":");
        assertThat(version.substring(version.indexOf(':') + 1)).hasSize(12);
    }

    @Test
    @DisplayName("모델이 바뀌면 자동 fingerprint도 달라진다")
    void currentVersionChangesWhenModelChanges() {
        MockQuestionCacheProperties properties = MockQuestionCachePropertiesTestSupport.createProperties();
        MockQuestionCacheVersionProvider baseline = new MockQuestionCacheVersionProvider(
                properties,
                "gpt-4o-mini",
                "embed-v4.0",
                3,
                5
        );
        MockQuestionCacheVersionProvider changedModel = new MockQuestionCacheVersionProvider(
                properties,
                "gpt-5-mini",
                "embed-v4.0",
                3,
                5
        );

        assertThat(changedModel.currentVersion()).isNotEqualTo(baseline.currentVersion());
    }
}
