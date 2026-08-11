package com.jobdri.jobdri_api.domain.analysis.infrastructure.ai;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisResponseParser;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiAnalysisAdapterTest {

    private final OpenAIClient openAIClient = mock(OpenAIClient.class);
    private final LlmConcurrencyLimiter llmConcurrencyLimiter = mock(LlmConcurrencyLimiter.class);
    private final AsyncMetricsRecorder asyncMetricsRecorder = mock(AsyncMetricsRecorder.class);
    private final AnalysisResponseParser analysisResponseParser = mock(AnalysisResponseParser.class);

    private OpenAiAnalysisAdapter openAiAnalysisAdapter;

    @BeforeEach
    void setUp() {
        openAiAnalysisAdapter = new OpenAiAnalysisAdapter(
                openAIClient,
                llmConcurrencyLimiter,
                asyncMetricsRecorder,
                analysisResponseParser
        );
        ReflectionTestUtils.setField(openAiAnalysisAdapter, "analysisModel", "gpt-4o-mini");
    }

    @Test
    @DisplayName("정상 응답이면 limiter 실행 후 success 메트릭을 기록한다")
    void createStructuredResponseRecordsSuccessMetric() throws Exception {
        StructuredResponse<String> response = mock(StructuredResponse.class);
        when(llmConcurrencyLimiter.execute(eq("analysis"), any())).thenReturn(response);
        when(analysisResponseParser.extractStructuredContent(response)).thenReturn("ok");

        String result = openAiAnalysisAdapter.createStructuredResponse("analysis", "prompt", String.class);

        assertThat(result).isEqualTo("ok");
        verify(llmConcurrencyLimiter).execute(eq("analysis"), any());
        verify(asyncMetricsRecorder).recordLlmRequest(eq("analysis"), eq("success"), any(Long.class));
        verify(asyncMetricsRecorder, never()).recordLlmRequest(eq("analysis"), eq("error"), any(Long.class));
    }

    @Test
    @DisplayName("limiter 실행 중 RuntimeException이 나면 그대로 전파하고 error 메트릭을 기록한다")
    void createStructuredResponsePropagatesLimiterFailure() {
        RuntimeException exception = new RuntimeException("boom");
        when(llmConcurrencyLimiter.execute(eq("analysis"), any())).thenThrow(exception);

        assertThatThrownBy(() -> openAiAnalysisAdapter.createStructuredResponse("analysis", "prompt", String.class))
                .isSameAs(exception);

        verify(asyncMetricsRecorder).recordLlmRequest(eq("analysis"), eq("error"), any(Long.class));
    }

    @Test
    @DisplayName("응답 추출 실패면 success 없이 error 메트릭만 기록한다")
    void createStructuredResponseRecordsOnlyErrorWhenParsingFails() throws Exception {
        StructuredResponse<String> response = mock(StructuredResponse.class);
        when(llmConcurrencyLimiter.execute(eq("analysis"), any())).thenReturn(response);
        when(analysisResponseParser.extractStructuredContent(response))
                .thenThrow(new IllegalStateException("parse failed"));

        assertThatThrownBy(() -> openAiAnalysisAdapter.createStructuredResponse("analysis", "prompt", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parse failed");

        verify(asyncMetricsRecorder).recordLlmRequest(eq("analysis"), eq("error"), any(Long.class));
        verify(asyncMetricsRecorder, never()).recordLlmRequest(eq("analysis"), eq("success"), any(Long.class));
    }

    @Test
    @DisplayName("output text가 없으면 parser 예외를 전파하고 error 메트릭을 기록한다")
    void createStructuredResponsePropagatesParserFailureWhenOutputMissing() throws Exception {
        AnalysisResponseParser realParser = new AnalysisResponseParser();
        OpenAiAnalysisAdapter adapter = new OpenAiAnalysisAdapter(
                openAIClient,
                llmConcurrencyLimiter,
                asyncMetricsRecorder,
                realParser
        );
        ReflectionTestUtils.setField(adapter, "analysisModel", "gpt-4o-mini");

        StructuredResponse<String> response = mock(StructuredResponse.class);
        when(response.output()).thenReturn(List.of());
        when(llmConcurrencyLimiter.execute(eq("analysis"), any())).thenReturn(response);

        assertThatThrownBy(() -> adapter.createStructuredResponse("analysis", "prompt", String.class))
                .isInstanceOf(GeneralException.class);

        verify(asyncMetricsRecorder).recordLlmRequest(eq("analysis"), eq("error"), any(Long.class));
    }
}
