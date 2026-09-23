package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.global.cohere.CohereProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FewShotCanaryReadinessValidatorTest {

    @Test
    @DisplayName("정상적인 운영 승인 후보가 있으면 canary 준비 검증을 통과한다")
    void acceptsValidCanaryConfiguration() {
        FewShotProperties properties = canaryProperties();
        FewShotCaseStore caseStore = mock(FewShotCaseStore.class);
        when(caseStore.loadActiveCases()).thenReturn(approvedCanaryCases());

        var validator = validator(caseStore, properties);

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("canary에 평가 소스가 섞이면 시작을 거부한다")
    void rejectsMixedSourceConfiguration() {
        FewShotProperties properties = canaryProperties();
        properties.getSource().setReviewedEvaluationEnabled(true);

        var validator = validator(mock(FewShotCaseStore.class), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires only the reviewed production source.");
    }

    @Test
    @DisplayName("유효한 운영 승인 후보가 없으면 시작을 거부한다")
    void rejectsEmptyProductionDataset() {
        FewShotProperties properties = canaryProperties();
        FewShotCaseStore caseStore = mock(FewShotCaseStore.class);
        when(caseStore.loadActiveCases()).thenReturn(List.of());

        var validator = validator(caseStore, properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires at least one valid reviewed production case.");
    }

    @Test
    @DisplayName("worker rollout이 0이면 canary 시작을 거부한다")
    void rejectsDisabledWorkerRollout() {
        FewShotProperties properties = canaryProperties();
        properties.setWorkerRolloutPercentage(0);

        var validator = validator(mock(FewShotCaseStore.class), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires worker rollout percentage between 1 and 100.");
    }

    @Test
    @DisplayName("후보 datasetVersion이 설정과 다르면 시작을 거부한다")
    void rejectsMismatchedDatasetVersion() {
        FewShotProperties properties = canaryProperties();
        FewShotCaseStore caseStore = mock(FewShotCaseStore.class);
        when(caseStore.loadActiveCases()).thenReturn(List.of(caseItem(
                FewShotSource.REVIEWED_PRODUCTION,
                "different-version"
        )));

        var validator = validator(caseStore, properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary case datasetVersion must match the configured datasetVersion.");
    }

    @Test
    @DisplayName("승인 후보 ID가 누락되거나 예상하지 않은 ID가 있으면 시작을 거부한다")
    void rejectsUnexpectedCandidateIds() {
        FewShotProperties properties = canaryProperties();
        FewShotCaseStore caseStore = mock(FewShotCaseStore.class);
        when(caseStore.loadActiveCases()).thenReturn(List.of(
                caseItem("FS-02", FewShotSource.REVIEWED_PRODUCTION, properties.getDatasetVersion()),
                caseItem("FS-03", FewShotSource.REVIEWED_PRODUCTION, properties.getDatasetVersion()),
                caseItem("FS-05", FewShotSource.REVIEWED_PRODUCTION, properties.getDatasetVersion()),
                caseItem("FS-08", FewShotSource.REVIEWED_PRODUCTION, properties.getDatasetVersion()),
                caseItem("FS-10", FewShotSource.REVIEWED_PRODUCTION, properties.getDatasetVersion())
        ));

        var validator = validator(caseStore, properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires exactly the approved candidate IDs")
                .hasMessageContaining("FS-09");
    }

    @Test
    @DisplayName("검증기는 fewshot-canary profile에서만 등록된다")
    void isRestrictedToCanaryProfile() {
        Profile profile = FewShotCanaryReadinessValidator.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("fewshot-canary");
    }

    @Test
    @DisplayName("Cohere API 키가 비어 있으면 canary 시작을 거부한다")
    void rejectsBlankCohereApiKey() {
        FewShotProperties properties = canaryProperties();

        var validator = new FewShotCanaryReadinessValidator(
                mock(FewShotCaseStore.class),
                properties,
                cohereProperties(" ", 1024, Duration.ofSeconds(3), Duration.ofSeconds(15)),
                "single-pass"
        );

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires a Cohere API key.");
    }

    @Test
    @DisplayName("Cohere embedding 차원이 모델 지원값이 아니면 canary 시작을 거부한다")
    void rejectsUnsupportedCohereEmbeddingDimension() {
        FewShotProperties properties = canaryProperties();

        var validator = new FewShotCanaryReadinessValidator(
                mock(FewShotCaseStore.class),
                properties,
                cohereProperties("test-key", 1, Duration.ofSeconds(3), Duration.ofSeconds(15)),
                "single-pass"
        );

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires a valid Cohere embedding model and dimension.");
    }

    @Test
    @DisplayName("Cohere timeout이 양수가 아니면 canary 시작을 거부한다")
    void rejectsNonPositiveCohereConnectTimeout() {
        FewShotProperties properties = canaryProperties();

        var validator = new FewShotCanaryReadinessValidator(
                mock(FewShotCaseStore.class),
                properties,
                cohereProperties("test-key", 1024, Duration.ZERO, Duration.ofSeconds(15)),
                "single-pass"
        );

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires positive Cohere embedding timeouts.");
    }

    @Test
    @DisplayName("Cohere read timeout이 양수가 아니면 canary 시작을 거부한다")
    void rejectsNonPositiveCohereReadTimeout() {
        FewShotProperties properties = canaryProperties();

        var validator = new FewShotCanaryReadinessValidator(
                mock(FewShotCaseStore.class),
                properties,
                cohereProperties("test-key", 1024, Duration.ofSeconds(3), Duration.ZERO),
                "single-pass"
        );

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires positive Cohere embedding timeouts.");
    }

    private FewShotCanaryReadinessValidator validator(FewShotCaseStore caseStore, FewShotProperties properties) {
        return new FewShotCanaryReadinessValidator(
                caseStore,
                properties,
                cohereProperties("test-key", 1024, Duration.ofSeconds(3), Duration.ofSeconds(15)),
                "single-pass"
        );
    }

    private CohereProperties cohereProperties(
            String apiKey,
            int dimension,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        return new CohereProperties(
                apiKey,
                "https://api.cohere.com",
                new CohereProperties.Embedding("embed-v4.0", dimension, connectTimeout, readTimeout)
        );
    }

    private FewShotProperties canaryProperties() {
        FewShotProperties properties = new FewShotProperties();
        properties.setDynamicSelectionEnabled(true);
        properties.setWorkerRolloutPercentage(5);
        properties.setDatasetVersion("fewshot-pm-reviewed-20260914-v2");
        properties.setReviewedProductionResource(
                "analysis/fewshot/reviewed-fewshot-cases-pm-20260914-v2.json"
        );
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(false);
        properties.getSource().setReviewedProductionEnabled(true);
        return properties;
    }

    private List<FewShotCase> approvedCanaryCases() {
        return List.of("FS-02", "FS-03", "FS-05", "FS-08", "FS-09").stream()
                .map(id -> caseItem(id, FewShotSource.REVIEWED_PRODUCTION, "fewshot-pm-reviewed-20260914-v2"))
                .toList();
    }

    private FewShotCase caseItem(FewShotSource source, String datasetVersion) {
        return caseItem("FS-02", source, datasetVersion);
    }

    private FewShotCase caseItem(String id, FewShotSource source, String datasetVersion) {
        return new FewShotCase(
                id,
                source,
                FewShotReviewStatus.APPROVED,
                true,
                1,
                "백엔드",
                "Backend Engineer",
                List.of("API 개발"),
                List.of("Java"),
                "직무 경험",
                "비식별 답변",
                "{\"keyStrengths\":[],\"missingKeywords\":[],\"questionAnalyses\":[]}",
                List.of("api"),
                datasetVersion,
                "## 예시 FS-02"
        );
    }
}
