package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAiClient;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisAiClient.AnalysisAiCallResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(analysisAiClient.analyzeForEvaluationResult(any(AnalysisPromptInput.class), any()))
                .thenReturn(result(new AnalysisLlmResponse(
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
                )));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-01,AI·개발·데이터,백엔드,테스트 자동화 경험,SQL 활용 경험,대용량 처리,경험을 쓰세요,데이터 처리 경험이 있습니다.\n",
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
        assertThat(row.get("aiMissingKeywordsJson")).doesNotContain("preference");
        assertThat(row.get("aiMissingKeywordsJson")).doesNotContain("잘못된 출처");
        assertThat(row.get("aiQuestionAnalysesJson")).doesNotContain("답변에 없는 문장");
        assertThat(row.get("aiQuestionAnalysesJson")).doesNotContain("missing은 저장하지 않습니다.");
        verify(analysisAiClient).analyzeForEvaluationResult(any(AnalysisPromptInput.class), any());
    }

    @Test
    @DisplayName("평가 결과도 운영과 동일하게 missingKeywords와 improvement를 후처리한다")
    void runAppliesProductionSanitizationRules() throws Exception {
        AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationAnalysisBatchService service = new EvaluationAnalysisBatchService(
                analysisAiClient,
                new JobCategoryEvaluationCriteriaProvider(objectMapper),
                objectMapper
        );
        when(analysisAiClient.analyzeForEvaluationResult(any(AnalysisPromptInput.class), any()))
                .thenReturn(result(new AnalysisLlmResponse(
                        80,
                        70,
                        60,
                        "운영 후처리 검증",
                        List.of(
                                new AnalysisLlmResponse.MissingKeywordItem("영어 공인성적", "qualification"),
                                new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "qualification"),
                                new AnalysisLlmResponse.MissingKeywordItem("온라인 쇼핑몰 근무 경험자", "preference"),
                                new AnalysisLlmResponse.MissingKeywordItem("테스트 자동화 경험", "mainTask")
                        ),
                        List.of(
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "첫 번째 문장입니다.",
                                        "mentioned",
                                        "구체성이 부족합니다.",
                                        "첫 번째 문장입니다."
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "두 번째 문장입니다.",
                                        "mentioned",
                                        "결과가 부족합니다.",
                                        "세 번째 문장입니다."
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "세 번째 문장입니다.",
                                        "proven",
                                        "구체적인 성과 보완이 필요합니다.",
                                        "세 번째 문장입니다."
                                )
                        )
                )));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-01,AI·개발·데이터,백엔드,테스트 자동화 경험,SQL 활용 경험,온라인 쇼핑몰 근무 경험자,경험을 쓰세요,첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.\n",
                StandardCharsets.UTF_8
        );

        service.run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("aiMissingKeywordsJson"))
                .contains("SQL 활용 경험", "테스트 자동화 경험")
                .doesNotContain("영어 공인성적")
                .doesNotContain("온라인 쇼핑몰 근무 경험자");
        assertThat(row.get("aiQuestionAnalysesJson"))
                .contains("\"improvement\":\"\"")
                .doesNotContain("구체적인 성과 보완이 필요합니다.");
    }

    @Test
    @DisplayName("평가 결과도 운영과 동일하게 PROVEN/FABRICATED 상태 필터를 적용하고 raw/final 비교 정보를 남긴다")
    void runKeepsRawAndAppliesFinalStatusFilter() throws Exception {
        AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationAnalysisBatchService service = new EvaluationAnalysisBatchService(
                analysisAiClient,
                new JobCategoryEvaluationCriteriaProvider(objectMapper),
                objectMapper
        );
        when(analysisAiClient.analyzeForEvaluationResult(any(AnalysisPromptInput.class), any()))
                .thenReturn(result(new AnalysisLlmResponse(
                        80,
                        70,
                        60,
                        "raw/final 검증",
                        List.of(new AnalysisLlmResponse.HighlightItem(
                                "구체적인 강점입니다.",
                                "첫 번째 문장입니다."
                        )),
                        List.of(),
                        List.of(),
                        List.of(
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "첫 번째 문장입니다.",
                                        "proven",
                                        "근거가 충분합니다.",
                                        null
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "두 번째 문장입니다.",
                                        "mentioned",
                                        "실행 방법이 부족합니다.",
                                        null
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "세 번째 문장입니다.",
                                        "fabricated",
                                        "답변 내부의 명시적 사실과 직접 충돌합니다.",
                                        null
                                ),
                                new AnalysisLlmResponse.QuestionAnalysisItem(
                                        1L,
                                        "네 번째 문장입니다.",
                                        "fabricated",
                                        "",
                                        null
                                )
                        )
                )));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-05,AI·개발·데이터,백엔드,API 개발,Spring Boot 경험,,경험을 쓰세요,첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다. 네 번째 문장입니다.\n",
                StandardCharsets.UTF_8
        );

        service.run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("rawLlmResponseJson"))
                .contains("\"status\":\"proven\"")
                .contains("\"status\":\"fabricated\"");
        assertThat(row.get("aiQuestionAnalysesJson"))
                .contains("\"status\":\"mentioned\"")
                .contains("\"status\":\"fabricated\"")
                .doesNotContain("\"status\":\"proven\"")
                .doesNotContain("네 번째 문장입니다.");
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
        when(analysisAiClient.analyzeForEvaluationResult(any(AnalysisPromptInput.class), isNull()))
                .thenReturn(result(new AnalysisLlmResponse(70, 70, 70, "피드백", List.of(), List.of())));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-02,없는 중분류,백엔드,서버 개발,SQL,,경험을 쓰세요,SQL 경험이 있습니다.\n",
                StandardCharsets.UTF_8
        );

        service.run(input, output);

        verify(analysisAiClient).analyzeForEvaluationResult(any(AnalysisPromptInput.class), isNull());
    }

    @Test
    @DisplayName("필수 CSV header가 없으면 AI 호출 전에 실패한다")
    void runFailsFastWhenRequiredHeaderIsMissing() throws Exception {
        AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationAnalysisBatchService service = new EvaluationAnalysisBatchService(
                analysisAiClient,
                new JobCategoryEvaluationCriteriaProvider(objectMapper),
                objectMapper
        );
        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question\n"
                        + "EV-03,AI·개발·데이터,백엔드,서버 개발,SQL,,경험을 쓰세요\n",
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> service.run(input, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required headers")
                .hasMessageContaining("answer");
        verifyNoInteractions(analysisAiClient);
    }

    @Test
    @DisplayName("LLM 호출 실패 케이스도 실패 row로 CSV에 기록한다")
    void runWritesFailureRowWhenAnalyzeFails() throws Exception {
        AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationAnalysisBatchService service = new EvaluationAnalysisBatchService(
                analysisAiClient,
                new JobCategoryEvaluationCriteriaProvider(objectMapper),
                objectMapper
        );
        when(analysisAiClient.analyzeForEvaluationResult(any(AnalysisPromptInput.class), any()))
                .thenThrow(new RuntimeException("rate limit exceeded"));

        Path input = tempDir.resolve("evaluation_cases.csv");
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,mainTasks,qualifications,preferences,question,answer\n"
                        + "EV-04,AI·개발·데이터,백엔드,서버 개발,SQL,,경험을 쓰세요,SQL 경험이 있습니다.\n",
                StandardCharsets.UTF_8
        );

        EvaluationAnalysisBatchService.EvaluationBatchSummary summary = service.run(input, output);
        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.successCount()).isZero();
        assertThat(summary.failureCount()).isEqualTo(1);
        assertThat(row.get("caseId")).isEqualTo("EV-04");
        assertThat(row.get("aiMissingKeywordsJson")).isEqualTo("[]");
        assertThat(row.get("aiQuestionAnalysesJson")).isEqualTo("[]");
        assertThat(row.get("errorMessage")).contains("rate limit exceeded");
    }

    private AnalysisAiCallResult result(AnalysisLlmResponse response) {
        return new AnalysisAiCallResult(response, null, null, false, 0, 1);
    }
}
