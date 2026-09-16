package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotMetricsRecorderTest {

    @Test
    void recordsSelectionCallsAndBoundedFailureReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FewShotMetricsRecorder recorder = new FewShotMetricsRecorder(registry);

        recorder.recordSelection(FewShotSelectionMode.EMBEDDING, false, 3, 42);
        recorder.recordCohereLogicalCalls(2);
        recorder.recordCohereFailure("UnexpectedVendorException");
        recorder.recordCacheEvent("selection", "evicted", 2);

        assertThat(registry.get("fewshot.selection.count")
                .tags("mode", "EMBEDDING", "cache_hit", "false").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("fewshot.selection.duration")
                .tags("mode", "EMBEDDING", "cache_hit", "false").timer().count()).isEqualTo(1L);
        assertThat(registry.get("fewshot.selection.selected.candidates")
                .tags("mode", "EMBEDDING", "cache_hit", "false").summary().totalAmount()).isEqualTo(3.0);
        assertThat(registry.get("fewshot.cohere.logical.calls").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("fewshot.cohere.failure.count")
                .tag("reason", "Other").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("fewshot.cache.events")
                .tags("cache", "selection", "outcome", "evicted").counter().count()).isEqualTo(2.0);
    }
}
