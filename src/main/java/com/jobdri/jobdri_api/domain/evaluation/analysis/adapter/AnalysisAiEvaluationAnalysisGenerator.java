package com.jobdri.jobdri_api.domain.evaluation.analysis.adapter;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisAiClient.AnalysisAiCallResult;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptInput;
import com.jobdri.jobdri_api.domain.analysis.service.ai.JobCategoryEvaluationCriteriaProvider;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationAnalysisCommand;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationGeneratedResult;
import com.jobdri.jobdri_api.domain.evaluation.analysis.port.EvaluationAnalysisGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AnalysisAiEvaluationAnalysisGenerator implements EvaluationAnalysisGenerator {
    private static final Long EVALUATION_QUESTION_ID = 1L;

    private final AnalysisAiClient analysisAiClient;
    private final JobCategoryEvaluationCriteriaProvider jobCategoryEvaluationCriteriaProvider;

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

        AnalysisAiCallResult aiCallResult = analysisAiClient.analyzeForEvaluationResult(
                promptInput,
                jobCategoryEvaluationCriteriaProvider
                        .findByMiddleName(command.jobCategoryMiddle())
                        .orElse(null)
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
}
