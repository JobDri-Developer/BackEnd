package com.jobdri.jobdri_api.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class AuthRedisMetricsRecorder {

    private static final Duration[] DEFAULT_SLOS = new Duration[] {
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(250),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3)
    };

    private final MeterRegistry meterRegistry;

    public AuthRedisMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordBlacklistLookup(String outcome, long durationMillis) {
        timer("auth.redis.blacklist.lookup.duration", Tags.of("outcome", outcome))
                .record(durationMillis, TimeUnit.MILLISECONDS);
        counter("auth.redis.blacklist.lookup.count", Tags.of("outcome", outcome))
                .increment();
    }

    public void incrementBlacklistFallback(String reason) {
        counter("auth.redis.blacklist.fallback.count", Tags.of("reason", reason))
                .increment();
    }

    private Timer timer(String name, Tags tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram()
                .serviceLevelObjectives(DEFAULT_SLOS)
                .register(meterRegistry);
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }
}
