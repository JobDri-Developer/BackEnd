package com.jobdri.jobdri_api.domain.analysis.infrastructure.ai;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisResponseParser;
import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAiAnalysisAdapter {
    private final OpenAIClient openAIClient;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;
    private final AsyncMetricsRecorder asyncMetricsRecorder;
    private final AnalysisResponseParser analysisResponseParser;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel = "gpt-4o-mini";

    public <T> T createStructuredResponse(String operationName, String prompt, Class<T> responseType) {
        var params = ResponseCreateParams.builder()
                .model(analysisModel)
                .input(prompt)
                .temperature(0.2)
                .text(responseType)
                .build();
        long startedAt = System.nanoTime();
        try {
            StructuredResponse<T> response = llmConcurrencyLimiter.execute(
                    operationName,
                    () -> openAIClient.responses().create(params)
            );
            asyncMetricsRecorder.recordLlmRequest(operationName, "success", elapsedMillis(startedAt));
            return analysisResponseParser.extractStructuredContent(response);
        } catch (RuntimeException e) {
            asyncMetricsRecorder.recordLlmRequest(operationName, "error", elapsedMillis(startedAt));
            throw e;
        } catch (Error e) {
            asyncMetricsRecorder.recordLlmRequest(operationName, "error", elapsedMillis(startedAt));
            throw e;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
