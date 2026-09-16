package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class FewShotMetricsRecorder {
    private static final Duration[] SELECTION_SLOS = {
            Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100),
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(10)
    };

    private final MeterRegistry meterRegistry;

    public FewShotMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSelection(FewShotSelectionMode mode, boolean cacheHit, int selectedCount, long durationMillis) {
        Tags tags = Tags.of("mode", mode.name(), "cache_hit", Boolean.toString(cacheHit));
        Counter.builder("fewshot.selection.count").tags(tags).register(meterRegistry).increment();
        Timer.builder("fewshot.selection.duration")
                .tags(tags)
                .publishPercentileHistogram()
                .serviceLevelObjectives(SELECTION_SLOS)
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
        DistributionSummary.builder("fewshot.selection.selected.candidates")
                .tags(tags)
                .register(meterRegistry)
                .record(selectedCount);
    }

    public void recordCohereLogicalCalls(long count) {
        if (count > 0) {
            Counter.builder("fewshot.cohere.logical.calls")
                    .register(meterRegistry)
                    .increment(count);
        }
    }

    public void recordCohereFailure(String reason) {
        Counter.builder("fewshot.cohere.failure.count")
                .tag("reason", normalizeReason(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordCacheEvent(String cache, String outcome, long count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("fewshot.cache.events")
                .tags("cache", cache, "outcome", outcome)
                .register(meterRegistry)
                .increment(count);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Unknown";
        }
        return switch (reason) {
            case "GeneralException", "IllegalArgumentException", "IllegalStateException",
                 "TimeoutException", "ExecutionException" -> reason;
            default -> "Other";
        };
    }
}
