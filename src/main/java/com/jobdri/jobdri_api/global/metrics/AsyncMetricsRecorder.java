package com.jobdri.jobdri_api.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AsyncMetricsRecorder {

    private static final Duration[] DEFAULT_SLOS = new Duration[] {
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(250),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30)
    };

    private final MeterRegistry meterRegistry;

    public AsyncMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void bindLlmConcurrencyMetrics(Semaphore semaphore, int maxConcurrentRequests) {
        AtomicInteger maxPermits = new AtomicInteger(maxConcurrentRequests);
        Gauge.builder("llm.concurrency.available.permits", semaphore, Semaphore::availablePermits)
                .register(meterRegistry);
        Gauge.builder("llm.concurrency.max.permits", maxPermits, AtomicInteger::get)
                .register(meterRegistry);
    }

    public void recordQueueWait(String taskType, long durationMillis) {
        timer("async.queue.wait.duration", Tags.of("task_type", taskType))
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordProcessing(String taskType, String outcome, long durationMillis) {
        timer("async.processing.duration", Tags.of("task_type", taskType, "outcome", outcome))
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordLlmRequest(String operation, String outcome, long durationMillis) {
        timer("llm.request.duration", Tags.of("operation", operation, "outcome", outcome))
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void recordLlmConcurrencyAcquire(String operation, String outcome, long durationMillis) {
        timer("llm.concurrency.acquire.duration", Tags.of("operation", operation, "outcome", outcome))
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public void incrementLlmConcurrencyTimeout(String operation) {
        counter("llm.concurrency.timeout.count", Tags.of("operation", operation))
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
