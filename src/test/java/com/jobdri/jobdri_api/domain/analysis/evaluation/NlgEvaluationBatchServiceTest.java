package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NlgEvaluationBatchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("judge 점수 범위를 검증하고 유효한 결과만 평균에 반영한다")
    void validatesScoreRange() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-01",
                        List.of(new NlgEvaluationResponse.QuestionAnalysisEvaluation(
                                0,
                                "좋은 문장을 문제로 잡았습니다.",
                                6,
                                5,
                                5,
                                5,
                                5,
                                5,
                                5,
                                5,
                                5,
                                5,
                                List.of(NlgEvaluationErrorCode.FALSE_POSITIVE_ANALYSIS)
                        )),
                        4,
                        5,
                        4,
                        4,
                        3,
                        3,
                        List.of(NlgEvaluationErrorCode.FALSE_POSITIVE_ANALYSIS),
                        "좋은 문장을 첨삭 대상으로 잡은 오류가 있습니다."
                ),
                120L,
                10,
                20
        ));

        Path input = writeJudgeInput("EV-01", analysesJson(List.of(
                new EvaluationQuestionAnalysisResult(
                        1L,
                        "좋은 문장을 문제로 잡았습니다.",
                        "mentioned",
                        "부족합니다.",
                        null,
                        0,
                        16
                )
        )));
        Path output = tempDir.resolve("judge.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("analysisCount")).isEqualTo("1");
        assertThat(row.get("averageRelevance")).isBlank();
        assertThat(row.get("strengthsPrecision")).isEqualTo("5");
        assertThat(row.get("errorCodes")).contains("FALSE_POSITIVE_ANALYSIS");
    }

    @Test
    @DisplayName("questionAnalyses가 비어 있으면 문장 단위 평균을 비워둔다")
    void keepsAnalysisAveragesBlankForEmptyQuestionAnalyses() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-02",
                        List.of(),
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        List.of(NlgEvaluationErrorCode.NONE),
                        "분석 대상 문장이 없어 케이스 단위만 평가했습니다."
                ),
                90L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-02", "[]");
        Path output = tempDir.resolve("judge_empty.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("analysisCount")).isEqualTo("0");
        assertThat(row.get("averageProblemValidity")).isBlank();
        assertThat(row.get("overallUsefulness")).isEqualTo("5");
        assertThat(row.get("failureStage")).isBlank();
    }

    @Test
    @DisplayName("메타 improvement, 새 사실 생성, 시제 변경 오류 코드를 검증 후 CSV에 기록한다")
    void writesValidatedErrorCodesForNlgFailures() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-03",
                        List.of(new NlgEvaluationResponse.QuestionAnalysisEvaluation(
                                null,
                                "입사 후 SQL로 자동화하겠습니다.",
                                4,
                                4,
                                3,
                                4,
                                4,
                                1,
                                1,
                                2,
                                1,
                                2,
                                List.of(
                                        NlgEvaluationErrorCode.META_IMPROVEMENT,
                                        NlgEvaluationErrorCode.UNSUPPORTED_FACT,
                                        NlgEvaluationErrorCode.TENSE_CHANGED
                                )
                        )),
                        3,
                        4,
                        2,
                        3,
                        2,
                        3,
                        List.of(NlgEvaluationErrorCode.INVALID_MISSING_KEYWORD),
                        "메타 첨삭과 원문에 없는 계획 생성이 함께 보입니다."
                ),
                150L,
                30,
                40
        ));

        Path input = writeJudgeInput("EV-03", analysesJson(List.of(
                new EvaluationQuestionAnalysisResult(
                        1L,
                        "입사 후 SQL로 자동화하겠습니다.",
                        "mentioned",
                        "구체성이 부족합니다.",
                        "SQL 자동화 계획을 추가하겠습니다.",
                        0,
                        18
                )
        )));
        Path output = tempDir.resolve("judge_errors.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("errorCodes"))
                .contains("META_IMPROVEMENT")
                .contains("UNSUPPORTED_FACT")
                .contains("TENSE_CHANGED")
                .contains("INVALID_MISSING_KEYWORD");
        assertThat(row.get("averageFaithfulness")).isEqualTo("1.0");
    }

    @Test
    @DisplayName("낮은 criterion 점수와 NONE-only 조합은 NONE을 제거한다")
    void removesNoneWhenLowCriterionScoreExists() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-06",
                        List.of(new NlgEvaluationResponse.QuestionAnalysisEvaluation(
                                0,
                                "좋은 문장을 문제로 잡았습니다.",
                                5,
                                2,
                                5,
                                4,
                                5,
                                5,
                                5,
                                5,
                                5,
                                5,
                                List.of(NlgEvaluationErrorCode.NONE)
                        )),
                        4,
                        5,
                        5,
                        5,
                        5,
                        3,
                        List.of(NlgEvaluationErrorCode.NONE),
                        "낮은 점수와 NONE이 함께 반환된 케이스입니다."
                ),
                100L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-06", analysesJson(List.of(new EvaluationQuestionAnalysisResult(
                1L,
                "좋은 문장을 문제로 잡았습니다.",
                "mentioned",
                "부족합니다.",
                null,
                0,
                16
        ))));
        Path output = tempDir.resolve("judge_none_removed.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("errorCodes")).isEqualTo("[]");
        assertThat(row.get("averageProblemValidity")).isEqualTo("2.0");
    }

    @Test
    @DisplayName("errorCode와 NONE이 동시에 있으면 NONE을 제거한다")
    void removesNoneWhenOtherErrorCodeExists() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-07",
                        List.of(),
                        2,
                        5,
                        4,
                        5,
                        2,
                        2,
                        List.of(NlgEvaluationErrorCode.NONE, NlgEvaluationErrorCode.MISSED_ANALYSIS),
                        "빈 분석이지만 명백한 문제를 놓쳤습니다."
                ),
                100L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-07", "[]");
        Path output = tempDir.resolve("judge_missed_analysis.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("analysisCount")).isEqualTo("0");
        assertThat(row.get("noAnalysisAppropriateness")).isEqualTo("2");
        assertThat(row.get("errorCodes"))
                .contains("MISSED_ANALYSIS")
                .doesNotContain("NONE");
    }

    @Test
    @DisplayName("strength와 missing keyword coverage 평가를 CSV에 기록한다")
    void writesCoverageScores() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-08",
                        List.of(),
                        5,
                        5,
                        2,
                        5,
                        2,
                        3,
                        List.of(NlgEvaluationErrorCode.MISSED_STRENGTH, NlgEvaluationErrorCode.MISSED_MISSING_KEYWORD),
                        "강점과 누락 키워드 coverage가 낮습니다."
                ),
                100L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-08", "[]");
        Path output = tempDir.resolve("judge_coverage.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("strengthsCoverage")).isEqualTo("2");
        assertThat(row.get("missingKeywordsCoverage")).isEqualTo("2");
        assertThat(row.get("errorCodes"))
                .contains("MISSED_STRENGTH")
                .contains("MISSED_MISSING_KEYWORD");
    }

    @Test
    @DisplayName("치명 오류가 있으면 overallUsefulness 4~5점은 검증에서 제외한다")
    void rejectsHighOverallUsefulnessWithFatalError() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-09",
                        List.of(),
                        5,
                        5,
                        5,
                        5,
                        5,
                        4,
                        List.of(NlgEvaluationErrorCode.UNSUPPORTED_FACT),
                        "치명 오류가 있는데 overallUsefulness가 높습니다."
                ),
                100L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-09", "[]");
        Path output = tempDir.resolve("judge_overall.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("overallUsefulness")).isBlank();
        assertThat(row.get("errorCodes")).contains("UNSUPPORTED_FACT");
    }

    @Test
    @DisplayName("judge 실패 행은 failureStage로 기록한다")
    void recordsJudgeFailureRow() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenThrow(new IllegalStateException("judge unavailable"));

        Path input = writeJudgeInput("EV-04", "[]");
        Path output = tempDir.resolve("judge_failure.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("caseId")).isEqualTo("EV-04");
        assertThat(row.get("failureStage")).isEqualTo("judge_call_failed");
    }

    @Test
    @DisplayName("judge 출력 CSV는 입력 파일을 덮어쓰지 않는다")
    void rejectsSameInputAndOutput() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        Path input = writeJudgeInput("EV-05", "[]");

        assertThatThrownBy(() -> new NlgEvaluationBatchService(aiClient, objectMapper).run(input, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not overwrite");
    }

    @Test
    @DisplayName("비교 리포트는 치명 오류율과 평균 지표를 함께 출력한다")
    void writesComparisonReport() throws Exception {
        Path judgeResult = tempDir.resolve("judge_result.csv");
        NlgEvaluationCsvSupport.write(judgeResult, List.of(
                new NlgEvaluationResult(
                        "EV-01",
                        "source.csv",
                        1,
                        4.0,
                        3.0,
                        4.0,
                        4.0,
                        5.0,
                        2.0,
                        5.0,
                        4.0,
                        1.0,
                        3.0,
                        5,
                        5,
                        2,
                        4,
                        2,
                        4,
                        objectMapper.writeValueAsString(List.of(
                                "META_IMPROVEMENT",
                                "UNSUPPORTED_FACT",
                                "FALSE_POSITIVE_ANALYSIS"
                        )),
                        "요약",
                        100,
                        40,
                        300L,
                        ""
                )
        ));
        Path output = tempDir.resolve("compare.csv");

        new NlgEvaluationBatchService(mock(NlgEvaluationAiClient.class), objectMapper)
                .compare(List.of(judgeResult), output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("averageProblemValidity")).isEqualTo("3.0");
        assertThat(row.get("metaImprovementRate")).isEqualTo("100.0");
        assertThat(row.get("fatalErrorRate")).isEqualTo("100.0");
    }

    private Path writeJudgeInput(String caseId, String analysesJson) throws Exception {
        Path input = tempDir.resolve(caseId + ".csv");
        String rawLlmResponseJson = objectMapper.writeValueAsString(new AnalysisLlmResponse(
                70,
                60,
                65,
                "피드백",
                List.of(new AnalysisLlmResponse.HighlightItem("강점", "좋은 문장")),
                List.of(),
                List.of(),
                List.of()
        ));
        Files.writeString(
                input,
                String.join(",", List.of(
                        "caseId",
                        "mainTasks",
                        "qualifications",
                        "preferences",
                        "question",
                        "answer",
                        "aiQuestionAnalysesJson",
                        "aiMissingKeywordsJson",
                        "rawLlmResponseJson",
                        "rawCandidateResponseJson",
                        "candidateReviewResponseJson"
                )) + "\n"
                        + csv(caseId) + ","
                        + csv("재고 분석") + ","
                        + csv("장애 대응 경험") + ","
                        + csv("SQL 우대") + ","
                        + csv("지원 동기") + ","
                        + csv("좋은 문장을 문제로 잡았습니다. 입사 후 SQL로 자동화하겠습니다.") + ","
                        + csv(analysesJson) + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson) + ","
                        + csv("") + ","
                        + csv("") + "\n",
                StandardCharsets.UTF_8
        );
        return input;
    }

    private String analysesJson(List<EvaluationQuestionAnalysisResult> analyses) throws Exception {
        return objectMapper.writeValueAsString(analyses);
    }

    private String csv(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }
}
