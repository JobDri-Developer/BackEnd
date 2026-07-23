package com.jobdri.jobdri_api.global.config;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class LlmConcurrencyLimiter {

    private final Semaphore semaphore;
    private final long acquireTimeoutMillis;
    private final AsyncMetricsRecorder asyncMetricsRecorder;

    public LlmConcurrencyLimiter(
            AsyncMetricsRecorder asyncMetricsRecorder,
            @Value("${llm.concurrency.max-concurrent-requests:4}") int maxConcurrentRequests,
            @Value("${llm.concurrency.acquire-timeout-millis:3000}") long acquireTimeoutMillis
    ) {
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("llm.concurrency.max-concurrent-requests must be positive");
        }
        if (acquireTimeoutMillis <= 0) {
            throw new IllegalArgumentException("llm.concurrency.acquire-timeout-millis must be positive");
        }
        this.asyncMetricsRecorder = asyncMetricsRecorder;
        this.semaphore = new Semaphore(maxConcurrentRequests, true);
        this.acquireTimeoutMillis = acquireTimeoutMillis;
        this.asyncMetricsRecorder.bindLlmConcurrencyMetrics(this.semaphore, maxConcurrentRequests);
    }

    public <T> T execute(String operationName, CheckedSupplier<T> supplier) {
        boolean acquired = false;
        long acquireStartedAt = System.nanoTime();
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            asyncMetricsRecorder.recordLlmConcurrencyAcquire(
                    operationName,
                    acquired ? "acquired" : "timeout",
                    elapsedMillis(acquireStartedAt)
            );
            if (!acquired) {
                asyncMetricsRecorder.incrementLlmConcurrencyTimeout(operationName);
                log.warn(
                        "LLM concurrency limiter timeout. operation={}, availablePermits={}",
                        operationName,
                        semaphore.availablePermits()
                );
                throw new GeneralException(
                        GeneralErrorCode.SERVICE_UNAVAILABLE,
                        "현재 AI 요청이 많아 처리 대기 시간이 길어지고 있습니다. 잠시 후 다시 시도해주세요."
                );
            }
            return supplier.get();
        } catch (InterruptedException e) {
            asyncMetricsRecorder.recordLlmConcurrencyAcquire(
                    operationName,
                    "interrupted",
                    elapsedMillis(acquireStartedAt)
            );
            Thread.currentThread().interrupt();
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "AI 요청 대기 중 인터럽트가 발생했습니다."
            );
        } catch (GeneralException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
