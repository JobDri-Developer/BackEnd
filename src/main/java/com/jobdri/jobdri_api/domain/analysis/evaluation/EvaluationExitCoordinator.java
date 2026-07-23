package com.jobdri.jobdri_api.domain.analysis.evaluation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
class EvaluationExitCoordinator {
    private final ConfigurableApplicationContext applicationContext;
    private final SpringExitOperation springExitOperation;
    private final SystemExitOperation systemExitOperation;
    private final AtomicBoolean exitRequested = new AtomicBoolean(false);

    @Autowired
    EvaluationExitCoordinator(ConfigurableApplicationContext applicationContext) {
        this(
                applicationContext,
                (context, exitCode) -> SpringApplication.exit(context, () -> exitCode),
                System::exit
        );
    }

    EvaluationExitCoordinator(
            ConfigurableApplicationContext applicationContext,
            SpringExitOperation springExitOperation,
            SystemExitOperation systemExitOperation
    ) {
        this.applicationContext = applicationContext;
        this.springExitOperation = springExitOperation;
        this.systemExitOperation = systemExitOperation;
    }

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
            int resolvedExitCode = exitCode;
            try {
                resolvedExitCode = springExitOperation.exit(applicationContext, exitCode);
            } catch (RuntimeException e) {
                log.warn(
                        "Evaluation context shutdown failed. source={}, requestedExitCode={}",
                        source,
                        exitCode,
                        e
                );
            } finally {
                log.info(
                        "Evaluation exit requested. source={}, requestedExitCode={}, resolvedExitCode={}",
                        source,
                        exitCode,
                        resolvedExitCode
                );
                systemExitOperation.exit(resolvedExitCode);
            }
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    interface SpringExitOperation {
        int exit(ConfigurableApplicationContext applicationContext, int exitCode);
    }

    interface SystemExitOperation {
        void exit(int exitCode);
    }
}
