package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Profile("fewshot-canary")
@Slf4j
class FewShotCanaryReadinessValidator implements SmartInitializingSingleton {
    private final FewShotCaseStore caseStore;
    private final FewShotProperties properties;
    private final String analysisMode;

    FewShotCanaryReadinessValidator(
            FewShotCaseStore caseStore,
            FewShotProperties properties,
            @Value("${analysis.mode:}") String analysisMode
    ) {
        this.caseStore = caseStore;
        this.properties = properties;
        this.analysisMode = analysisMode;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validateConfiguration();
        List<FewShotCase> activeCases = caseStore.loadActiveCases();
        if (activeCases.isEmpty()) {
            throw new IllegalStateException("fewshot-canary requires at least one valid reviewed production case.");
        }
        if (activeCases.stream().anyMatch(item -> item.source() != FewShotSource.REVIEWED_PRODUCTION)) {
            throw new IllegalStateException("fewshot-canary allows only REVIEWED_PRODUCTION cases.");
        }
        if (activeCases.stream().anyMatch(item -> !properties.getDatasetVersion().equals(item.datasetVersion()))) {
            throw new IllegalStateException("fewshot-canary case datasetVersion must match the configured datasetVersion.");
        }
        log.info(
                "fewshot-canary readiness validated. datasetVersion={}, candidateCount={}, candidateIds={}",
                properties.getDatasetVersion(),
                activeCases.size(),
                activeCases.stream().map(FewShotCase::id).toList()
        );
    }

    private void validateConfiguration() {
        if (!"single-pass".equalsIgnoreCase(analysisMode)) {
            throw new IllegalStateException("fewshot-canary requires analysis.mode=single-pass.");
        }
        if (!properties.isDynamicSelectionEnabled()) {
            throw new IllegalStateException("fewshot-canary requires dynamic selection to be enabled.");
        }
        if (properties.getSource().isFixedEnabled()
                || properties.getSource().isCuratedEnabled()
                || properties.getSource().isReviewedEvaluationEnabled()
                || !properties.getSource().isReviewedProductionEnabled()) {
            throw new IllegalStateException("fewshot-canary requires only the reviewed production source.");
        }
        if (!StringUtils.hasText(properties.getReviewedProductionResource())) {
            throw new IllegalStateException("fewshot-canary requires a reviewed production resource.");
        }
        if (!StringUtils.hasText(properties.getDatasetVersion())) {
            throw new IllegalStateException("fewshot-canary requires a datasetVersion.");
        }
    }
}
