package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisWorkerFewShotSelectorTest {

    @Test
    void rolloutBoundaryIncludesNoneAtZeroAndAllAtOneHundred() {
        FewShotProperties properties = new FewShotProperties();
        AnalysisWorkerFewShotSelector selector = new AnalysisWorkerFewShotSelector(
                properties,
                (AnalysisPromptBuilder) null
        );

        assertThat(properties.getWorkerRolloutPercentage()).isZero();
        assertThat(selector.isIncluded("task-1")).isFalse();

        properties.setWorkerRolloutPercentage(100);
        assertThat(selector.isIncluded("task-1")).isTrue();
    }

    @Test
    void fivePercentRolloutUsesStableTaskIdBucket() {
        FewShotProperties properties = new FewShotProperties();
        properties.setWorkerRolloutPercentage(5);
        AnalysisWorkerFewShotSelector selector = new AnalysisWorkerFewShotSelector(
                properties,
                (AnalysisPromptBuilder) null
        );

        long included = IntStream.range(0, 10_000)
                .mapToObj(index -> "task-" + index)
                .filter(selector::isIncluded)
                .count();

        assertThat(included).isBetween(400L, 600L);
        assertThat(selector.isIncluded("stable-task"))
                .isEqualTo(selector.isIncluded("stable-task"));
    }

    @Test
    void rolloutPercentageRejectsOutOfRangeValues() {
        FewShotProperties properties = new FewShotProperties();

        assertThatThrownBy(() -> properties.setWorkerRolloutPercentage(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setWorkerRolloutPercentage(101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
