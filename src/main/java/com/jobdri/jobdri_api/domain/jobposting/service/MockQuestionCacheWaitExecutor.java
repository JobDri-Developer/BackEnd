package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockQuestionCacheWaitExecutor {

    private static final long EXECUTION_TIMEOUT_BUFFER_MILLIS = 1_000L;

    private final ThreadPoolExecutor executor;

    public MockQuestionCacheWaitExecutor(MockQuestionCacheProperties properties) {
        AtomicInteger threadCounter = new AtomicInteger(1);
        this.executor = new ThreadPoolExecutor(
                properties.getWaitExecutorPoolSize(),
                properties.getWaitExecutorPoolSize(),
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.getWaitExecutorQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("mock-question-cache-wait-" + threadCounter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.allowCoreThreadTimeOut(true);
    }

    public <T> T execute(Callable<T> task, long timeoutMillis) {
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException exception) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "추천 질문 캐시 대기 요청이 많아 잠시 후 다시 시도해주세요."
            );
        }

        try {
            return future.get(Math.max(1L, timeoutMillis) + EXECUTION_TIMEOUT_BUFFER_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "추천 질문 생성 대기 중 인터럽트가 발생했습니다."
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "추천 질문 생성이 처리 중입니다. 잠시 후 다시 시도해주세요."
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("추천 질문 캐시 대기 중 알 수 없는 오류가 발생했습니다.", cause);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
