package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

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
        when(caseStore.loadActiveCases()).thenReturn(List.of(caseItem(
                FewShotSource.REVIEWED_PRODUCTION,
                "fewshot-pm-reviewed-20260914-v2"
        )));

        var validator = new FewShotCanaryReadinessValidator(caseStore, properties, "single-pass");

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("canary에 평가 소스가 섞이면 시작을 거부한다")
    void rejectsMixedSourceConfiguration() {
        FewShotProperties properties = canaryProperties();
        properties.getSource().setReviewedEvaluationEnabled(true);

        var validator = new FewShotCanaryReadinessValidator(mock(FewShotCaseStore.class), properties, "single-pass");

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

        var validator = new FewShotCanaryReadinessValidator(caseStore, properties, "single-pass");

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary requires at least one valid reviewed production case.");
    }

    @Test
    @DisplayName("worker rollout이 0이면 canary 시작을 거부한다")
    void rejectsDisabledWorkerRollout() {
        FewShotProperties properties = canaryProperties();
        properties.setWorkerRolloutPercentage(0);

        var validator = new FewShotCanaryReadinessValidator(mock(FewShotCaseStore.class), properties, "single-pass");

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

        var validator = new FewShotCanaryReadinessValidator(caseStore, properties, "single-pass");

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fewshot-canary case datasetVersion must match the configured datasetVersion.");
    }

    @Test
    @DisplayName("검증기는 fewshot-canary profile에서만 등록된다")
    void isRestrictedToCanaryProfile() {
        Profile profile = FewShotCanaryReadinessValidator.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("fewshot-canary");
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

    private FewShotCase caseItem(FewShotSource source, String datasetVersion) {
        return new FewShotCase(
                "FS-02",
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
