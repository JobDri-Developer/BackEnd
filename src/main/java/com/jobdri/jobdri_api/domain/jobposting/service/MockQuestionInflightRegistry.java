package com.jobdri.jobdri_api.domain.jobposting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

@Component
@RequiredArgsConstructor
public class MockQuestionInflightRegistry {

    private final ConcurrentHashMap<String, FutureTask<java.util.List<String>>> inflightTasks = new ConcurrentHashMap<>();

    public java.util.List<String> execute(String key, TaskSupplier supplier) {
        FutureTask<java.util.List<String>> task = new FutureTask<>(supplier::get);
        FutureTask<java.util.List<String>> existingTask = inflightTasks.putIfAbsent(key, task);

        if (existingTask == null) {
            try {
                task.run();
                return task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("추천 질문 생성 대기 중 인터럽트가 발생했습니다.", e);
            } catch (ExecutionException e) {
                throw unwrap(e);
            } finally {
                inflightTasks.remove(key, task);
            }
        }

        try {
            return existingTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("추천 질문 생성 대기 중 인터럽트가 발생했습니다.", e);
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    private RuntimeException unwrap(ExecutionException executionException) {
        Throwable cause = executionException.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("추천 질문 생성 중 알 수 없는 오류가 발생했습니다.", cause);
    }

    @FunctionalInterface
    public interface TaskSupplier {
        java.util.List<String> get() throws Exception;
    }
}
