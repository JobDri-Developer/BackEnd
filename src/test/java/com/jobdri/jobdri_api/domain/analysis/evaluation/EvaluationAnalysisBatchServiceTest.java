package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisPromptInput;
import com.jobdri.jobdri_api.domain.analysis.service.JobCategoryEvaluationCriteriaProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationAnalysisBatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("LLM 응답을 검증해 평가 결과 CSV로 저장한다")
    void runWritesSanitizedEvaluationResults() throws Exception {
        AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationAnalysisBatchService service = new EvaluationAnalysisBatchService(
                analysisAiClient,
                new JobCategoryEvaluationCriteriaProvider(objectMapper),
                objectMapper
        );
        when(analysisAiClient.analyzeForEvaluation(any(AnalysisPromptInput.class), any()))
                .thenReturn(new AnalysisLlmResponse(
                        80,
                        70,
                        60,
                        "검증 피드백",
                        List.of(
                                new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "qualification"),
                                new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "preference"),
                                new AnalysisLlmResponse.MissingKeywordItem(" ", "mainTask"),
                                new AnalysisLlmResponse.MissingKeywordItem("잘못된 출처", "unknown"),
                                new AnalysisLlmResponse.MissingKeywordItem("테스트 자동화 경험", "mainTask")
                        ),
                        List.of(
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "데이터 처리 경험이 있습니다.",
                                        "mentioned",
                                        "구체성이 부족합니다.",
                                        "데이터 처리 경험을 바탕으로 배치 성능을 개선했습니다."
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "답변에 없는 문장",
                                        "proven",
                                        "원문에 없습니다.",
                                        "무시되어야 합니다."
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "데이터 처리 경험이 있습니다.",
                                        "missing",
                                        "missing은 저장하지 않습니다.",
                                        "무시되어야 합니다."
                                )
                        )
                ));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-01,AI·개발·데이터,백엔드,서버 개발,SQL,대용량 처리,경험을 쓰세요,데이터 처리 경험이 있습니다.\n",
                StandardCharsets.UTF_8
        );

        EvaluationAnalysisBatchService.EvaluationBatchSummary summary = service.run(input, output);

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.successCount()).isEqualTo(1);
        String csv = Files.readString(output, StandardCharsets.UTF_8);
        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(csv).contains("EV-01");
        assertThat(csv).contains(",73,80,70,60,");
        assertThat(row.get("aiMissingKeywordsJson")).contains("SQL 활용 경험");
        assertThat(row.get("aiMissingKeywordsJson")).contains("테스트 자동화 경험");
        assertThat(row.get("aiMissingKeywordsJson")).doesNotContain("잘못된 출처");
        assertThat(row.get("aiQuestionAnalysesJson")).doesNotContain("답변에 없는 문장");
        assertThat(row.get("aiQuestionAnalysesJson")).doesNotContain("missing은 저장하지 않습니다.");
        verify(analysisAiClient).analyzeForEvaluation(any(AnalysisPromptInput.class), any());
    }

    @Test
    @DisplayName("없는 직무 중분류는 보조 기준 없이 분석한다")
    void runOmitsCriteriaWhenMiddleNameNotFound() throws Exception {
        AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationAnalysisBatchService service = new EvaluationAnalysisBatchService(
                analysisAiClient,
                new JobCategoryEvaluationCriteriaProvider(objectMapper),
                objectMapper
        );
        when(analysisAiClient.analyzeForEvaluation(any(AnalysisPromptInput.class), isNull()))
                .thenReturn(new AnalysisLlmResponse(70, 70, 70, "피드백", List.of(), List.of()));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-02,없는 중분류,백엔드,서버 개발,SQL,,경험을 쓰세요,SQL 경험이 있습니다.\n",
                StandardCharsets.UTF_8
        );

        service.run(input, output);

        verify(analysisAiClient).analyzeForEvaluation(any(AnalysisPromptInput.class), isNull());
    }
}
