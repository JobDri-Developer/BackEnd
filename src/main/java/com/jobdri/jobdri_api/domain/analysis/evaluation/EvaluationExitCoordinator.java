package com.jobdri.jobdri_api.domain.analysis.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
class EvaluationExitCoordinator {
    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean exitRequested = new AtomicBoolean(false);

    void exit(int exitCode) {
        if (!exitRequested.compareAndSet(false, true)) {
            return;
        }
        Thread shutdownThread = new Thread(() -> {
            int resolvedExitCode = SpringApplication.exit(applicationContext, () -> exitCode);
            System.exit(resolvedExitCode);
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }
}
