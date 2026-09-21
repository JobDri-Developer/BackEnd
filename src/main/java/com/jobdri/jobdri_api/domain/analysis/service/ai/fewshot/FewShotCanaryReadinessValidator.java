package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Profile("fewshot-canary")
@Slf4j
class FewShotCanaryReadinessValidator implements SmartInitializingSingleton {
    private static final Set<String> EXPECTED_CANDIDATE_IDS = Set.of(
            "FS-02", "FS-03", "FS-05", "FS-08", "FS-09"
    );

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
        Set<String> activeCandidateIds = new HashSet<>(activeCases.stream().map(FewShotCase::id).toList());
        if (activeCases.size() != EXPECTED_CANDIDATE_IDS.size()
                || !activeCandidateIds.equals(EXPECTED_CANDIDATE_IDS)) {
            throw new IllegalStateException(
                    "fewshot-canary requires exactly the approved candidate IDs: " + EXPECTED_CANDIDATE_IDS
            );
        }
        log.info(
                "fewshot-canary readiness validated. datasetVersion={}, workerRolloutPercentage={}, candidateCount={}, candidateIds={}",
                properties.getDatasetVersion(),
                properties.getWorkerRolloutPercentage(),
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
        if (properties.getWorkerRolloutPercentage() <= 0
                || properties.getWorkerRolloutPercentage() > 100) {
            throw new IllegalStateException("fewshot-canary requires worker rollout percentage between 1 and 100.");
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
