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

    @Value("${evaluation.hybrid-merge.enabled:false}")
    private boolean hybridMergeEnabled;

    @Value("${evaluation.missing-keyword-replay.enabled:false}")
    private boolean missingKeywordReplayEnabled;

    @Override
    public void afterSingletonsInstantiated() {
        int enabledRunnerCount = 0;
        enabledRunnerCount += analysisEvaluationEnabled ? 1 : 0;
        enabledRunnerCount += nlgJudgeEnabled ? 1 : 0;
        enabledRunnerCount += hybridMergeEnabled ? 1 : 0;
        enabledRunnerCount += missingKeywordReplayEnabled ? 1 : 0;
        if (enabledRunnerCount > 1) {
            throw new IllegalStateException(
                    "analysis-eval runner flags are mutually exclusive: evaluation.analysis.enabled, evaluation.nlg-judge.enabled, evaluation.hybrid-merge.enabled, evaluation.missing-keyword-replay.enabled"
            );
        }
    }
}
