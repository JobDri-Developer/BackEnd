package com.jobdri.jobdri_api.domain.analysis.infrastructure.ai;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisResponseParser;
import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OpenAiAnalysisAdapter {
    private final OpenAIClient openAIClient;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;
    private final AsyncMetricsRecorder asyncMetricsRecorder;
    private final AnalysisResponseParser analysisResponseParser;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel;

    public <T> T createStructuredResponse(String operationName, String prompt, Class<T> responseType) {
        return createStructuredResponse(operationName, prompt, responseType, null);
    }

    public <T> T createStructuredResponse(
            String operationName,
            String prompt,
            Class<T> responseType,
            Duration timeout
    ) {
        var params = ResponseCreateParams.builder()
                .model(analysisModel)
                .input(prompt)
                .temperature(0.2)
                .text(responseType)
                .build();
        RequestOptions requestOptions = timeout == null
                ? null
                : RequestOptions.builder().timeout(timeout).build();
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            StructuredResponse<T> response = llmConcurrencyLimiter.execute(
                    operationName,
                    () -> requestOptions == null
                            ? openAIClient.responses().create(params)
                            : openAIClient.responses().create(params, requestOptions)
            );
            T structuredContent = analysisResponseParser.extractStructuredContent(response);
            success = true;
            return structuredContent;
        } finally {
            asyncMetricsRecorder.recordLlmRequest(
                    operationName,
                    success ? "success" : "error",
                    elapsedMillis(startedAt)
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
