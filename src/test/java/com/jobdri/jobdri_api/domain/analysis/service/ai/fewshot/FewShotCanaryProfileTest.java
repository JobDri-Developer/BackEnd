package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.service.ai.FewShotPromptProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotCanaryProfileTest {

    @Test
    @DisplayName("fewshot-canary profile은 승인된 운영 후보 5건만 사용한다")
    void loadsOnlyReviewedProductionCandidates() {
        Properties yaml = loadCanaryProperties();
        FewShotProperties properties = propertiesFrom(yaml);

        var loaded = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper())
                .loadActiveCases();

        assertThat(yaml.getProperty("analysis.mode")).isEqualTo("single-pass");
        assertThat(properties.isDynamicSelectionEnabled()).isTrue();
        assertThat(properties.getWorkerRolloutPercentage()).isEqualTo(5);
        assertThat(properties.getDatasetVersion()).isEqualTo("fewshot-pm-reviewed-20260914-v2");
        assertThat(loaded)
                .extracting(FewShotCase::id)
                .containsExactly("FS-02", "FS-03", "FS-05", "FS-08", "FS-09");
        assertThat(loaded)
                .extracting(FewShotCase::source)
                .containsOnly(FewShotSource.REVIEWED_PRODUCTION);
    }

    private Properties loadCanaryProperties() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application-fewshot-canary.yaml"));
        Properties properties = factoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }

    private FewShotProperties propertiesFrom(Properties yaml) {
        FewShotProperties properties = new FewShotProperties();
        properties.setDynamicSelectionEnabled(Boolean.parseBoolean(
                yaml.getProperty("analysis.few-shot.dynamic-selection-enabled")
        ));
        properties.setWorkerRolloutPercentage(Integer.parseInt(
                yaml.getProperty("analysis.few-shot.worker-rollout-percentage")
        ));
        properties.setDatasetVersion(yaml.getProperty("analysis.few-shot.dataset-version"));
        properties.setReviewedProductionResource(
                yaml.getProperty("analysis.few-shot.reviewed-production-resource")
        );
        properties.getSearch().setTopK(Integer.parseInt(
                yaml.getProperty("analysis.few-shot.search.top-k")
        ));
        properties.getSearch().setMinSimilarity(Double.parseDouble(
                yaml.getProperty("analysis.few-shot.search.min-similarity")
        ));
        properties.getSearch().setMinimumSelectedCount(Integer.parseInt(
                yaml.getProperty("analysis.few-shot.search.minimum-selected-count")
        ));
        properties.getSource().setFixedEnabled(Boolean.parseBoolean(
                yaml.getProperty("analysis.few-shot.source.fixed-enabled")
        ));
        properties.getSource().setCuratedEnabled(Boolean.parseBoolean(
                yaml.getProperty("analysis.few-shot.source.curated-enabled")
        ));
        properties.getSource().setReviewedEvaluationEnabled(Boolean.parseBoolean(
                yaml.getProperty("analysis.few-shot.source.reviewed-evaluation-enabled")
        ));
        properties.getSource().setReviewedProductionEnabled(Boolean.parseBoolean(
                yaml.getProperty("analysis.few-shot.source.reviewed-production-enabled")
        ));
        return properties;
    }
}
