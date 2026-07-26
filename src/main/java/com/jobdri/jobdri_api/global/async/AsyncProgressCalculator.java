package com.jobdri.jobdri_api.global.async;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Component
public class AsyncProgressCalculator {

    public String resolveCurrentStep(AsyncTaskProgressStatus status, String currentStep, String defaultStep) {
        if (status == AsyncTaskProgressStatus.SUCCEEDED) {
            return "COMPLETED";
        }
        return currentStep == null || currentStep.isBlank() ? defaultStep : currentStep;
    }

    public Integer resolveProgressPercent(AsyncTaskProgressStatus status, Integer progressPercent) {
        if (status == AsyncTaskProgressStatus.SUCCEEDED) {
            return 100;
        }
        if (status == AsyncTaskProgressStatus.FAILED || status == AsyncTaskProgressStatus.CANCELLED) {
            return 0;
        }
        return progressPercent == null ? 0 : Math.max(0, Math.min(100, progressPercent));
    }

    public Integer resolveEstimatedRemainingSeconds(
            AsyncTaskProgressStatus status,
            Integer estimatedRemainingSeconds,
            LocalDateTime startedAt,
            int defaultEstimatedRemainingSeconds
    ) {
        if (status == AsyncTaskProgressStatus.SUCCEEDED
                || status == AsyncTaskProgressStatus.FAILED
                || status == AsyncTaskProgressStatus.CANCELLED) {
            return 0;
        }
        if (estimatedRemainingSeconds != null) {
            return Math.max(0, estimatedRemainingSeconds);
        }
        if (startedAt == null) {
            return defaultEstimatedRemainingSeconds;
        }
        long elapsedSeconds = Math.max(0L, Duration.between(startedAt, LocalDateTime.now()).toSeconds());
        return (int) Math.max(0L, defaultEstimatedRemainingSeconds - elapsedSeconds);
    }

    public <T> List<T> buildSteps(
            AsyncTaskProgressStatus status,
            String currentStep,
            List<ProgressStepDefinition> steps,
            Function<ProgressStep, T> mapper
    ) {
        int currentIndex = indexOfStep(currentStep, steps);
        int effectiveCurrentIndex = currentIndex < 0 ? 0 : currentIndex;
        return steps.stream()
                .map(step -> new ProgressStep(
                        step.code(),
                        step.label(),
                        resolveStepStatus(status, effectiveCurrentIndex, indexOfStep(step.code(), steps))
                ))
                .map(mapper)
                .toList();
    }

    public int indexOfStep(String code, List<ProgressStepDefinition> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).code().equals(code)) {
                return i;
            }
        }
        return -1;
    }

    private String resolveStepStatus(AsyncTaskProgressStatus taskStatus, int currentIndex, int stepIndex) {
        if (taskStatus == AsyncTaskProgressStatus.SUCCEEDED) {
            return "COMPLETED";
        }
        if (taskStatus == AsyncTaskProgressStatus.CANCELLED) {
            return stepIndex <= currentIndex ? "CANCELLED" : "PENDING";
        }
        if (taskStatus == AsyncTaskProgressStatus.FAILED) {
            return stepIndex < currentIndex ? "COMPLETED" : stepIndex == currentIndex ? "FAILED" : "PENDING";
        }
        return stepIndex < currentIndex ? "COMPLETED" : stepIndex == currentIndex ? "IN_PROGRESS" : "PENDING";
    }

    public enum AsyncTaskProgressStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public record ProgressStepDefinition(String code, String label) {
    }

    public record ProgressStep(String code, String label, String status) {
    }
}
