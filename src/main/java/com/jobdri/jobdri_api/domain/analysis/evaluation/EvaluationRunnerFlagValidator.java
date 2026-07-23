package com.jobdri.jobdri_api.domain.analysis.evaluation;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("analysis-eval")
class EvaluationRunnerFlagValidator implements SmartInitializingSingleton {
    @Value("${evaluation.analysis.enabled:false}")
    private boolean analysisEvaluationEnabled;

    @Value("${evaluation.nlg-judge.enabled:false}")
    private boolean nlgJudgeEnabled;

    @Override
    public void afterSingletonsInstantiated() {
        if (analysisEvaluationEnabled && nlgJudgeEnabled) {
            throw new IllegalStateException(
                    "evaluation.analysis.enabled와 evaluation.nlg-judge.enabled를 동시에 true로 설정할 수 없습니다."
            );
        }
    }
}
