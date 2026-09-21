package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.worker.AnalysisWorkerFewShotContext;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptBuilder;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor
public class AnalysisWorkerFewShotSelector {
    private final FewShotProperties properties;
    private final AnalysisPromptBuilder analysisPromptBuilder;

    public AnalysisWorkerFewShotContext select(String taskId, AnalysisExecutionPayload payload) {
        if (!properties.isDynamicSelectionEnabled() || !isIncluded(taskId)) {
            return null;
        }

        AnalysisPromptInput promptInput = AnalysisPromptInput
                .from(payload.jobPosting(), payload.answeredQuestions())
                .withCaseId(taskId);
        AnalysisPromptBuilder.WorkerFewShotPrompt selected =
                analysisPromptBuilder.resolveFewShotForWorker(promptInput);
        return new AnalysisWorkerFewShotContext(selected.promptBlock(), selected.metadata());
    }

    boolean isIncluded(String taskId) {
        int percentage = properties.getWorkerRolloutPercentage();
        if (percentage <= 0 || taskId == null || taskId.isBlank()) {
            return false;
        }
        return percentage >= 100 || bucket(taskId) < percentage;
    }

    static int bucket(String taskId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(taskId.getBytes(StandardCharsets.UTF_8));
            return Math.floorMod(ByteBuffer.wrap(digest).getInt(), 100);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
