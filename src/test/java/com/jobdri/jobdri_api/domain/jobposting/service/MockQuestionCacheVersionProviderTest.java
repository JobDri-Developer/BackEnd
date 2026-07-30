package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.cohere.CohereProperties;
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
                cohereProperties("embed-v4.0"),
                "gpt-4o-mini",
                3,
                5
        );
        MockQuestionCacheVersionProvider changedModel = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v4.0"),
                "gpt-5-mini",
                3,
                5
        );

        assertThat(changedModel.currentVersion()).isNotEqualTo(baseline.currentVersion());
    }

    @Test
    @DisplayName("임베딩 모델이 바뀌면 자동 fingerprint도 달라진다")
    void currentVersionChangesWhenEmbeddingModelChanges() {
        MockQuestionCacheProperties properties = MockQuestionCachePropertiesTestSupport.createProperties();
        MockQuestionCacheVersionProvider baseline = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v4.0"),
                "gpt-4o-mini",
                3,
                5
        );
        MockQuestionCacheVersionProvider changedEmbeddingModel = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v5.0"),
                "gpt-4o-mini",
                3,
                5
        );

        assertThat(changedEmbeddingModel.currentVersion()).isNotEqualTo(baseline.currentVersion());
    }

    @Test
    @DisplayName("JD retrieval limit이 바뀌면 자동 fingerprint도 달라진다")
    void currentVersionChangesWhenJdLimitChanges() {
        MockQuestionCacheProperties properties = MockQuestionCachePropertiesTestSupport.createProperties();
        MockQuestionCacheVersionProvider baseline = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v4.0"),
                "gpt-4o-mini",
                3,
                5
        );
        MockQuestionCacheVersionProvider changedJdLimit = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v4.0"),
                "gpt-4o-mini",
                4,
                5
        );

        assertThat(changedJdLimit.currentVersion()).isNotEqualTo(baseline.currentVersion());
    }

    @Test
    @DisplayName("문항 retrieval limit이 바뀌면 자동 fingerprint도 달라진다")
    void currentVersionChangesWhenQuestionLimitChanges() {
        MockQuestionCacheProperties properties = MockQuestionCachePropertiesTestSupport.createProperties();
        MockQuestionCacheVersionProvider baseline = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v4.0"),
                "gpt-4o-mini",
                3,
                5
        );
        MockQuestionCacheVersionProvider changedQuestionLimit = new MockQuestionCacheVersionProvider(
                properties,
                cohereProperties("embed-v4.0"),
                "gpt-4o-mini",
                3,
                6
        );

        assertThat(changedQuestionLimit.currentVersion()).isNotEqualTo(baseline.currentVersion());
    }

    private CohereProperties cohereProperties(String model) {
        return new CohereProperties(
                "test-api-key",
                "https://api.cohere.com",
                new CohereProperties.Embedding(model, 1024, null, null)
        );
    }
}
