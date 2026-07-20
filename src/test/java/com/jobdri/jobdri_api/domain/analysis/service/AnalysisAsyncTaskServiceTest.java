package com.jobdri.jobdri_api.domain.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.AnalysisAsyncTask;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisAsyncTaskServiceTest {

    @Mock
    private AnalysisAsyncTaskRepository analysisAsyncTaskRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AnalysisAsyncSseService analysisAsyncSseService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AnalysisAsyncTaskService analysisAsyncTaskService;

    @Test
    @DisplayName("worker LLM 결과 payload를 저장하고 다시 조회한다")
    void storeAndFindWorkerResult() {
        AnalysisAsyncTask task = AnalysisAsyncTask.pending(1L, 10L, 3);
        AnalysisLlmResponse llmResponse = new AnalysisLlmResponse(
                80,
                70,
                90,
                "총평입니다.",
                List.of(new AnalysisLlmResponse.HighlightItem("강점", "실제 답변 인용")),
                List.of(new AnalysisLlmResponse.HighlightItem("약점", "공고 인용")),
                List.of(new AnalysisLlmResponse.MissingKeywordItem("Spring", "qualification")),
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(100L, "실제 문장", "proven", "근거", "개선"))
        );

        when(analysisAsyncTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        analysisAsyncTaskService.storeWorkerResult(task.getTaskId(), llmResponse);
        Optional<AnalysisLlmResponse> stored = analysisAsyncTaskService.findWorkerResult(task.getTaskId());

        assertThat(stored).isPresent();
        assertThat(stored.get().jobFit()).isEqualTo(80);
        assertThat(stored.get().keyStrengths()).hasSize(1);
        assertThat(stored.get().keyWeaknesses()).hasSize(1);
        assertThat(stored.get().missingKeywords()).hasSize(1);
        assertThat(stored.get().questionAnalyses().get(0).status()).isEqualTo("proven");
    }
}
