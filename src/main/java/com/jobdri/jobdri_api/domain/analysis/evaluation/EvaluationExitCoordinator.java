package com.jobdri.jobdri_api.domain.analysis.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
class EvaluationExitCoordinator {
    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean exitRequested = new AtomicBoolean(false);

    void exit(String source, int exitCode) {
        if (!exitRequested.compareAndSet(false, true)) {
            log.info(
                    "Evaluation exit request ignored. source={}, requestedExitCode={}",
                    source,
                    exitCode
            );
            return;
        }
        Thread shutdownThread = new Thread(() -> {
            int resolvedExitCode = SpringApplication.exit(applicationContext, () -> exitCode);
            log.info(
                    "Evaluation exit requested. source={}, requestedExitCode={}, resolvedExitCode={}",
                    source,
                    exitCode,
                    resolvedExitCode
            );
            System.exit(resolvedExitCode);
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }
}
