package com.jobdri.jobdri_api.domain.evaluation.analysis.adapter;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisAiClient.AnalysisAiCallResult;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptInput;
import com.jobdri.jobdri_api.domain.analysis.service.ai.JobCategoryEvaluationCriteriaProvider;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationAnalysisCommand;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationGeneratedResult;
import com.jobdri.jobdri_api.domain.evaluation.analysis.port.EvaluationAnalysisGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AnalysisAiEvaluationAnalysisGenerator implements EvaluationAnalysisGenerator {
    private static final Long EVALUATION_QUESTION_ID = 1L;

    private final AnalysisAiClient analysisAiClient;
    private final JobCategoryEvaluationCriteriaProvider jobCategoryEvaluationCriteriaProvider;

    @Value("${evaluation.analysis.case-timeout.single-pass-seconds:70}")
    private long singlePassCaseTimeoutSeconds;

    @Value("${evaluation.analysis.case-timeout.two-pass-seconds:130}")
    private long twoPassCaseTimeoutSeconds;

    @Value("${evaluation.analysis.case-timeout.hybrid-exact-seconds:190}")
    private long hybridExactCaseTimeoutSeconds;

    @Override
    public EvaluationGeneratedResult generate(EvaluationAnalysisCommand command) {
        AnalysisPromptInput promptInput = new AnalysisPromptInput(
                command.caseId(),
                "평가용 회사",
                command.jobCategorySmall(),
                command.mainTasks(),
                command.qualifications(),
                command.preferences(),
                List.of(new AnalysisPromptInput.QuestionAnswer(
                        EVALUATION_QUESTION_ID,
                        command.question(),
                        command.answer()
                ))
        );
        Instant deadline = Instant.now().plus(resolveCaseBudget());

        AnalysisAiCallResult aiCallResult = analysisAiClient.analyzeForEvaluationResult(
                promptInput,
                jobCategoryEvaluationCriteriaProvider
                        .findByMiddleName(command.jobCategoryMiddle())
                        .orElse(null),
                deadline
        );
        return new EvaluationGeneratedResult(
                aiCallResult.response(),
                aiCallResult.rawCandidateResponse(),
                aiCallResult.sanitizedCandidateResponse(),
                aiCallResult.candidateReviewResponse(),
                aiCallResult.candidateCallLatencyMs(),
                aiCallResult.finalCallLatencyMs()
        );
    }

    private Duration resolveCaseBudget() {
        long seconds = switch (analysisAiClient.resolveAnalysisMode()) {
            case SINGLE_PASS -> singlePassCaseTimeoutSeconds;
            case TWO_PASS -> twoPassCaseTimeoutSeconds;
            case HYBRID_EXACT -> hybridExactCaseTimeoutSeconds;
        };
        return Duration.ofSeconds(Math.max(1L, seconds));
    }
}
