package com.jobdri.jobdri_api.domain.evaluation.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("analysis-eval")
@RequiredArgsConstructor
@Slf4j
class EvaluationRunnerDiagnostics implements SmartInitializingSingleton {
    private final ListableBeanFactory beanFactory;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> runnerBeanNames = Arrays.stream(beanFactory.getBeanNamesForType(ApplicationRunner.class))
                .sorted()
                .toList();
        log.info("analysis-eval ApplicationRunner beans={}", runnerBeanNames);
    }
}
