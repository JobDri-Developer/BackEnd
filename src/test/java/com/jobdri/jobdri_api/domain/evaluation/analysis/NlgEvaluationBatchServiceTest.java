package com.jobdri.jobdri_api.domain.evaluation.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        verify(aiClient, times(1)).evaluate(any());
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
        verify(aiClient, times(1)).evaluate(any());
    }

    @Test
    @DisplayName("빈 questionAnalyses에 대해 judge가 문장 평가를 생성하면 빈 평가 배열로 정규화하고 case-level 결과를 보존한다")
    void normalizesHallucinatedQuestionEvaluationsForEmptyQuestionAnalyses() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-02",
                        hallucinatedQuestionEvaluations(2),
                        2,
                        3,
                        4,
                        1,
                        2,
                        3,
                        List.of(NlgEvaluationErrorCode.MISSED_ANALYSIS),
                        "빈 분석이지만 중요한 첨삭 대상을 놓쳤습니다."
                ),
                90L,
                11,
                22
        ));

        Path input = writeJudgeInput("EV-02", "[]");
        Path output = tempDir.resolve("judge_empty_normalized.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("analysisCount")).isEqualTo("0");
        assertThat(row.get("averageProblemValidity")).isBlank();
        assertThat(row.get("noAnalysisAppropriateness")).isEqualTo("2");
        assertThat(row.get("strengthsPrecision")).isEqualTo("3");
        assertThat(row.get("strengthsCoverage")).isEqualTo("4");
        assertThat(row.get("missingKeywordsPrecision")).isEqualTo("1");
        assertThat(row.get("missingKeywordsCoverage")).isEqualTo("2");
        assertThat(row.get("overallUsefulness")).isEqualTo("3");
        assertThat(row.get("errorCodes")).contains("MISSED_ANALYSIS");
        assertThat(row.get("shortRationale")).isEqualTo("빈 분석이지만 중요한 첨삭 대상을 놓쳤습니다.");
        assertThat(row.get("failureStage")).isBlank();
        assertThat(row.get("judgeInputTokens")).isEqualTo("11");
        assertThat(row.get("judgeOutputTokens")).isEqualTo("22");
        verify(aiClient, times(1)).evaluate(any());
    }

    @Test
    @DisplayName("caseId mismatch는 재시도하지 않고 validation failure로 기록한다")
    void doesNotRetryCaseIdMismatch() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                responseWithoutQuestionEvaluations("OTHER"),
                90L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-02", "[]");
        Path output = tempDir.resolve("judge_case_id_mismatch.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("failureStage")).isEqualTo("judge_validation_failed");
        verify(aiClient, times(1)).evaluate(any());
    }

    @Test
    @DisplayName("null judge response는 재시도하지 않고 validation failure로 기록한다")
    void doesNotRetryNullJudgeResponse() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                null,
                90L,
                null,
                null
        ));

        Path input = writeJudgeInput("EV-02", "[]");
        Path output = tempDir.resolve("judge_null_response.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("failureStage")).isEqualTo("judge_validation_failed");
        verify(aiClient, times(1)).evaluate(any());
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
                        List.of(new NlgEvaluationResponse.MissingKeywordMissEvaluation(
                                "장애 대응 경험",
                                "QUALIFICATION",
                                "장애 대응 경험",
                                "답변에서 해당 경험을 확인할 수 없습니다."
                        )),
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
    @DisplayName("MISSED_MISSING_KEYWORD는 JD requirement 근거가 유효하면 유지한다")
    void keepsMissedMissingKeywordWhenEvidenceIsGroundedInJd() throws Exception {
        Map<String, String> row = runJudgeWithMissedMissingKeywordEvidence(
                new NlgEvaluationResponse.MissingKeywordMissEvaluation(
                        "4대보험 신고",
                        "MAIN_TASK",
                        "4대보험 신고 및 지원금 신청",
                        "답변에서 4대보험 신고 경험을 확인할 수 없습니다."
                ),
                "4대보험 신고 및 지원금 신청",
                "엑셀 고급 활용",
                "[]"
        );

        assertThat(row.get("errorCodes")).contains("MISSED_MISSING_KEYWORD");
    }

    @Test
    @DisplayName("MISSED_MISSING_KEYWORD는 Spring Boot 실무 경험처럼 비정형 역량 근거가 유효하면 유지한다")
    void keepsTechnicalMissingKeywordEvidence() throws Exception {
        Map<String, String> row = runJudgeWithMissedMissingKeywordEvidence(
                new NlgEvaluationResponse.MissingKeywordMissEvaluation(
                        "Spring Boot 실무 경험",
                        "QUALIFICATION",
                        "Spring Boot 실무 경험",
                        "답변에서 Spring Boot 사용 경험을 확인할 수 없습니다."
                ),
                "REST API 개발",
                "Spring Boot 실무 경험",
                "[]"
        );

        assertThat(row.get("errorCodes")).contains("MISSED_MISSING_KEYWORD");
    }

    @Test
    @DisplayName("MISSED_MISSING_KEYWORD는 JD에 없는 추상 relatedRequirement면 제거한다")
    void removesMissedMissingKeywordWhenRelatedRequirementIsNotInJd() throws Exception {
        for (String relatedRequirement : List.of("채용 관련 경험", "핵심 경험")) {
            Map<String, String> row = runJudgeWithMissedMissingKeywordEvidence(
                    new NlgEvaluationResponse.MissingKeywordMissEvaluation(
                            relatedRequirement,
                            "MAIN_TASK",
                            relatedRequirement,
                            "답변에서 해당 경험을 확인할 수 없습니다."
                    ),
                    "엑셀 고급 활용, 4대보험 신고, 더존 사용",
                    "인사 회계 경험",
                    "[]"
            );

            assertThat(row.get("errorCodes")).doesNotContain("MISSED_MISSING_KEYWORD");
        }
    }

    @Test
    @DisplayName("MISSED_MISSING_KEYWORD는 정형 자격요건 근거면 제거한다")
    void removesStructuredQualificationMissedMissingKeywordEvidence() throws Exception {
        for (String keyword : List.of("사회복지사", "청소년상담사", "운전면허", "대졸", "경력 3년")) {
            Map<String, String> row = runJudgeWithMissedMissingKeywordEvidence(
                    new NlgEvaluationResponse.MissingKeywordMissEvaluation(
                            keyword,
                            "QUALIFICATION",
                            keyword,
                            "답변에서 해당 조건을 확인할 수 없습니다."
                    ),
                    "상담 지원",
                    keyword,
                    "[]"
            );

            assertThat(row.get("errorCodes")).doesNotContain("MISSED_MISSING_KEYWORD");
        }
    }

    @Test
    @DisplayName("MISSED_MISSING_KEYWORD는 이미 final missingKeywords에 있으면 제거한다")
    void removesMissedMissingKeywordAlreadyCoveredByFinalMissingKeywords() throws Exception {
        Map<String, String> row = runJudgeWithMissedMissingKeywordEvidence(
                new NlgEvaluationResponse.MissingKeywordMissEvaluation(
                        "4대보험 신고",
                        "MAIN_TASK",
                        "4대보험 신고 및 지원금 신청",
                        "답변에서 4대보험 신고 경험을 확인할 수 없습니다."
                ),
                "4대보험 신고 및 지원금 신청",
                "엑셀 고급 활용",
                objectMapper.writeValueAsString(List.of(new AnalysisLlmResponse.MissingKeywordItem(
                        "4대보험 신고",
                        "mainTask"
                )))
        );

        assertThat(row.get("errorCodes")).doesNotContain("MISSED_MISSING_KEYWORD");
    }

    @Test
    @DisplayName("MISSED_MISSING_KEYWORD는 evidence 없이 errorCode만 있으면 제거한다")
    void removesMissedMissingKeywordWithoutEvidence() throws Exception {
        Map<String, String> row = runJudgeWithMissedMissingKeywordEvidence(
                null,
                "4대보험 신고 및 지원금 신청",
                "엑셀 고급 활용",
                "[]"
        );

        assertThat(row.get("errorCodes")).doesNotContain("MISSED_MISSING_KEYWORD");
    }

    @Test
    @DisplayName("검증된 missing keyword 후보가 있는데 actual이 빈 배열이면 coverage와 errorCode를 서버에서 보정한다")
    void correctsEmptyActualMissingKeywordsWhenValidatedCandidatesExist() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-17",
                        List.of(),
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        List.of(NlgEvaluationErrorCode.NONE),
                        "누락 키워드를 높게 평가했습니다."
                ),
                100L,
                null,
                null
        ));
        Path input = writeJudgeInputWithMissingKeywordState(
                "EV-17",
                "[]",
                "{\"missingKeywordCandidates\":[{\"keyword\":\"장애 대응 경험\",\"source\":\"QUALIFICATION\",\"relatedRequirement\":\"장애 대응 경험\"}]}",
                "[]"
        );
        Path output = tempDir.resolve("judge_empty_missing_keywords.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("missingKeywordsCoverage")).isEqualTo("1");
        assertThat(row.get("errorCodes"))
                .contains("MISSED_MISSING_KEYWORD")
                .doesNotContain("NONE");
    }

    @Test
    @DisplayName("검증된 missing keyword 후보와 actual이 모두 비어 있으면 빈 배열을 정상 처리한다")
    void keepsEmptyActualMissingKeywordsWhenNoValidatedCandidateExists() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        stubJudge(aiClient, "EV-18");
        Path input = writeJudgeInputWithMissingKeywordState("EV-18", "[]", "{\"missingKeywordCandidates\":[]}", "[]");
        Path output = tempDir.resolve("judge_empty_missing_keywords_ok.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("missingKeywordsCoverage")).isEqualTo("5");
        assertThat(row.get("errorCodes")).contains("NONE");
    }

    @Test
    @DisplayName("actual missingKeywords JSON이 깨져 있으면 validation failure로 기록한다")
    void malformedActualMissingKeywordsFailsValidation() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        Path input = writeJudgeInputWithMissingKeywordState("EV-19", "not-json", "{\"missingKeywordCandidates\":[]}", "[]");
        Path output = tempDir.resolve("judge_malformed_missing_keywords.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("failureStage")).isEqualTo("judge_validation_failed");
        verify(aiClient, never()).evaluate(any());
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
        verify(aiClient, times(1)).evaluate(any());
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
    @DisplayName("표준 헤더 mainTasks/qualifications/question/answer를 직접 읽는다")
    void readsStandardHeaders() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        stubJudge(aiClient, "EV-10");
        Path input = writeJudgeInput("EV-10", "[]");
        Path output = tempDir.resolve("judge_standard.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        var captor = org.mockito.ArgumentCaptor.forClass(NlgEvaluationAiClient.NlgJudgeInput.class);
        verify(aiClient).evaluate(captor.capture());
        assertThat(captor.getValue().mainTasks()).isEqualTo("재고 분석");
        assertThat(captor.getValue().qualifications()).isEqualTo("장애 대응 경험");
        assertThat(captor.getValue().question()).isEqualTo("지원 동기");
        assertThat(captor.getValue().answer()).contains("좋은 문장을 문제로 잡았습니다.");
    }

    @Test
    @DisplayName("v5-A 결과 CSV는 같은 디렉터리의 evaluation_cases_reviewed.csv를 caseId로 보강해 읽는다")
    void readsV5aResultHeadersWithSourceCases() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        stubJudge(aiClient, "EV-11");
        writeSourceCases("EV-11", "주요 업무 원문", "자격요건 원문", "우대사항 원문", "문항 원문", "답변 원문");
        Path input = writeResultOnlyInput("EV-11");
        Path output = tempDir.resolve("judge_v5a.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        var captor = org.mockito.ArgumentCaptor.forClass(NlgEvaluationAiClient.NlgJudgeInput.class);
        verify(aiClient).evaluate(captor.capture());
        assertThat(captor.getValue().mainTasks()).isEqualTo("주요 업무 원문");
        assertThat(captor.getValue().qualifications()).isEqualTo("자격요건 원문");
        assertThat(captor.getValue().preferences()).isEqualTo("우대사항 원문");
        assertThat(captor.getValue().question()).isEqualTo("문항 원문");
        assertThat(captor.getValue().answer()).isEqualTo("답변 원문");
    }

    @Test
    @DisplayName("two-pass 평가 CSV의 실제 헤더는 원본 평가셋 보강 없이 직접 읽는다")
    void readsTwoPassResultHeadersDirectly() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        stubJudge(aiClient, "EV-12");
        Path input = tempDir.resolve("two_pass.csv");
        Files.writeString(
                input,
                String.join(",", List.of(
                        "caseId",
                        "jobCategoryMiddle",
                        "jobCategorySmall",
                        "mainTasks",
                        "qualifications",
                        "preferences",
                        "question",
                        "answer",
                        "aiScore",
                        "aiQuestionAnalysesJson",
                        "aiMissingKeywordsJson",
                        "rawLlmResponseJson",
                        "rawCandidateResponseJson",
                        "candidateReviewResponseJson"
                )) + "\n"
                        + csv("EV-12") + ",중분류,소분류,"
                        + csv("two-pass 주요 업무") + ","
                        + csv("two-pass 자격요건") + ","
                        + csv("two-pass 우대사항") + ","
                        + csv("two-pass 문항") + ","
                        + csv("two-pass 답변") + ",80,"
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson()) + ",,"
                        + "\n",
                StandardCharsets.UTF_8
        );
        Path output = tempDir.resolve("judge_two_pass.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        var captor = org.mockito.ArgumentCaptor.forClass(NlgEvaluationAiClient.NlgJudgeInput.class);
        verify(aiClient).evaluate(captor.capture());
        assertThat(captor.getValue().mainTasks()).isEqualTo("two-pass 주요 업무");
        assertThat(captor.getValue().answer()).isEqualTo("two-pass 답변");
    }

    @Test
    @DisplayName("입력 문맥이 JSON 컬럼 내부에 있으면 안전하게 추출한다")
    void readsInputContextFromJsonColumn() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        stubJudge(aiClient, "EV-13");
        Path input = tempDir.resolve("json_context.csv");
        String inputJson = objectMapper.writeValueAsString(Map.of(
                "mainTasks", "JSON 주요 업무",
                "qualifications", "JSON 자격요건",
                "preferences", "JSON 우대사항",
                "question", "JSON 문항",
                "answer", "JSON 답변"
        ));
        Files.writeString(
                input,
                "caseId,inputJson,aiQuestionAnalysesJson,aiMissingKeywordsJson,rawLlmResponseJson\n"
                        + csv("EV-13") + ","
                        + csv(inputJson) + ","
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson()) + "\n",
                StandardCharsets.UTF_8
        );
        Path output = tempDir.resolve("judge_json_context.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        var captor = org.mockito.ArgumentCaptor.forClass(NlgEvaluationAiClient.NlgJudgeInput.class);
        verify(aiClient).evaluate(captor.capture());
        assertThat(captor.getValue().mainTasks()).isEqualTo("JSON 주요 업무");
        assertThat(captor.getValue().question()).isEqualTo("JSON 문항");
    }

    @Test
    @DisplayName("필수 컬럼과 보강 원본이 모두 없으면 명확한 예외를 반환한다")
    void rejectsMissingRequiredInputHeaders() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        Path input = tempDir.resolve("missing_headers.csv");
        Files.writeString(
                input,
                "caseId,aiQuestionAnalysesJson,aiMissingKeywordsJson,rawLlmResponseJson\n"
                        + csv("EV-14") + ",[],[]," + csv(rawLlmResponseJson()) + "\n",
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> new NlgEvaluationBatchService(aiClient, objectMapper)
                .run(input, tempDir.resolve("missing_headers_output.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required headers or source data")
                .hasMessageContaining("mainTasks")
                .hasMessageContaining("evaluation_cases_reviewed.csv");
    }

    @Test
    @DisplayName("필수 셀이 비어 있으면 실패 행으로 기록한다")
    void recordsFailureForBlankRequiredCells() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        Path input = tempDir.resolve("blank_cell.csv");
        Files.writeString(
                input,
                "caseId,mainTasks,qualifications,question,answer,aiQuestionAnalysesJson,aiMissingKeywordsJson,rawLlmResponseJson\n"
                        + csv("EV-15") + ","
                        + csv("") + ","
                        + csv("자격요건") + ","
                        + csv("문항") + ","
                        + csv("답변") + ","
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson()) + "\n",
                StandardCharsets.UTF_8
        );
        Path output = tempDir.resolve("blank_cell_output.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        Map<String, String> row = EvaluationCsvSupport.read(output).getFirst();
        assertThat(row.get("caseId")).isEqualTo("EV-15");
        assertThat(row.get("failureStage")).isEqualTo("judge_validation_failed");
    }

    @Test
    @DisplayName("UTF-8 BOM이 있는 CSV 헤더도 정상 처리한다")
    void readsCsvWithUtf8Bom() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        stubJudge(aiClient, "EV-16");
        Path input = tempDir.resolve("bom.csv");
        Files.writeString(
                input,
                "\uFEFFcaseId,mainTasks,qualifications,preferences,question,answer,aiQuestionAnalysesJson,aiMissingKeywordsJson,rawLlmResponseJson\n"
                        + csv("EV-16") + ","
                        + csv("BOM 주요 업무") + ","
                        + csv("BOM 자격요건") + ","
                        + csv("") + ","
                        + csv("BOM 문항") + ","
                        + csv("BOM 답변") + ","
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson()) + "\n",
                StandardCharsets.UTF_8
        );
        Path output = tempDir.resolve("bom_output.csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        var captor = org.mockito.ArgumentCaptor.forClass(NlgEvaluationAiClient.NlgJudgeInput.class);
        verify(aiClient).evaluate(captor.capture());
        assertThat(captor.getValue().caseId()).isEqualTo("EV-16");
        assertThat(captor.getValue().mainTasks()).isEqualTo("BOM 주요 업무");
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
        Path judgeResultSecond = tempDir.resolve("judge_result_second.csv");
        NlgEvaluationCsvSupport.write(judgeResultSecond, List.of(
                new NlgEvaluationResult(
                        "EV-02",
                        "source-second.csv",
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        5,
                        4,
                        4,
                        5,
                        5,
                        5,
                        objectMapper.writeValueAsString(List.of("NONE")),
                        "요약",
                        80,
                        20,
                        200L,
                        ""
                )
        ));
        Path output = tempDir.resolve("nested").resolve("compare.csv");

        NlgEvaluationBatchService.NlgEvaluationComparisonSummary summary =
                new NlgEvaluationBatchService(mock(NlgEvaluationAiClient.class), objectMapper)
                .compare(List.of(judgeResult, judgeResultSecond), output);

        assertThat(EvaluationCsvSupport.read(judgeResult)).hasSize(1);
        assertThat(EvaluationCsvSupport.read(judgeResultSecond)).hasSize(1);
        assertThat(output).isRegularFile();
        assertThat(Files.size(output)).isGreaterThan(0);
        assertThat(summary.outputPath()).isEqualTo(output.toAbsolutePath().normalize());
        assertThat(summary.fileCount()).isEqualTo(2);
        assertThat(summary.summaryRowCount()).isEqualTo(2);
        assertThat(summary.sizeBytes()).isEqualTo(Files.size(output));
        List<Map<String, String>> outputRows = EvaluationCsvSupport.read(output);
        assertThat(outputRows).hasSize(2);
        Map<String, String> row = outputRows.getFirst();
        assertThat(row.get("caseCount")).isEqualTo("1");
        assertThat(row.get("successCount")).isEqualTo("1");
        assertThat(row.get("judgeFailedCount")).isEqualTo("0");
        assertThat(row.get("averageProblemValidity")).isEqualTo("3.0");
        assertThat(row.get("metaImprovementRate")).isEqualTo("100.0");
        assertThat(row.get("fatalErrorRate")).isEqualTo("100.0");
    }

    private Path writeJudgeInput(String caseId, String analysesJson) throws Exception {
        Path input = tempDir.resolve(caseId + ".csv");
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
                        + csv(rawLlmResponseJson()) + ","
                        + csv("") + ","
                        + csv("") + "\n",
                StandardCharsets.UTF_8
        );
        return input;
    }

    private Path writeJudgeInputWithMissingKeywordState(
            String caseId,
            String missingKeywordsJson,
            String sanitizedCandidateResponseJson,
            String analysesJson
    ) throws Exception {
        Path input = tempDir.resolve(caseId + "_missing.csv");
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
                        "sanitizedCandidateResponseJson",
                        "candidateReviewResponseJson"
                )) + "\n"
                        + csv(caseId) + ","
                        + csv("재고 분석") + ","
                        + csv("장애 대응 경험") + ","
                        + csv("") + ","
                        + csv("지원 동기") + ","
                        + csv("답변") + ","
                        + csv(analysesJson) + ","
                        + csv(missingKeywordsJson) + ","
                        + csv(rawLlmResponseJson()) + ","
                        + csv("") + ","
                        + csv(sanitizedCandidateResponseJson) + ","
                        + csv("") + "\n",
                StandardCharsets.UTF_8
        );
        return input;
    }

    private Map<String, String> runJudgeWithMissedMissingKeywordEvidence(
            NlgEvaluationResponse.MissingKeywordMissEvaluation evidence,
            String mainTasks,
            String qualifications,
            String missingKeywordsJson
    ) throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-20",
                        List.of(),
                        2,
                        5,
                        5,
                        2,
                        2,
                        3,
                        evidence == null ? List.of() : List.of(evidence),
                        List.of(NlgEvaluationErrorCode.MISSED_MISSING_KEYWORD),
                        "누락 키워드 근거를 평가했습니다."
                ),
                100L,
                null,
                null
        ));
        Path input = writeJudgeInputWithContext("EV-20", mainTasks, qualifications, missingKeywordsJson, "[]");
        Path output = tempDir.resolve("judge_missed_keyword_" + System.nanoTime() + ".csv");

        new NlgEvaluationBatchService(aiClient, objectMapper).run(input, output);

        return EvaluationCsvSupport.read(output).getFirst();
    }

    private Path writeJudgeInputWithContext(
            String caseId,
            String mainTasks,
            String qualifications,
            String missingKeywordsJson,
            String analysesJson
    ) throws Exception {
        Path input = tempDir.resolve(caseId + "_context_" + System.nanoTime() + ".csv");
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
                        "sanitizedCandidateResponseJson",
                        "candidateReviewResponseJson"
                )) + "\n"
                        + csv(caseId) + ","
                        + csv(mainTasks) + ","
                        + csv(qualifications) + ","
                        + csv("") + ","
                        + csv("지원 동기") + ","
                        + csv("답변") + ","
                        + csv(analysesJson) + ","
                        + csv(missingKeywordsJson) + ","
                        + csv(rawLlmResponseJson()) + ","
                        + csv("") + ","
                        + csv("{\"missingKeywordCandidates\":[]}") + ","
                        + csv("") + "\n",
                StandardCharsets.UTF_8
        );
        return input;
    }

    private void stubJudge(NlgEvaluationAiClient aiClient, String caseId) {
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                responseWithoutQuestionEvaluations(caseId),
                100L,
                10,
                20
        ));
    }

    private NlgEvaluationResponse responseWithoutQuestionEvaluations(String caseId) {
        return new NlgEvaluationResponse(
                caseId,
                List.of(),
                5,
                5,
                5,
                5,
                5,
                5,
                List.of(NlgEvaluationErrorCode.NONE),
                "정상 평가입니다."
        );
    }

    private List<NlgEvaluationResponse.QuestionAnalysisEvaluation> hallucinatedQuestionEvaluations(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new NlgEvaluationResponse.QuestionAnalysisEvaluation(
                        index,
                        "없는 분석 문장입니다. " + index,
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        List.of(NlgEvaluationErrorCode.NONE)
                ))
                .toList();
    }

    private void writeSourceCases(
            String caseId,
            String mainTasks,
            String qualifications,
            String preferences,
            String question,
            String answer
    ) throws Exception {
        Files.writeString(
                tempDir.resolve("evaluation_cases_reviewed.csv"),
                "caseId,mainTasks,qualifications,preferences,question,answer\n"
                        + csv(caseId) + ","
                        + csv(mainTasks) + ","
                        + csv(qualifications) + ","
                        + csv(preferences) + ","
                        + csv(question) + ","
                        + csv(answer) + "\n",
                StandardCharsets.UTF_8
        );
    }

    private Path writeResultOnlyInput(String caseId) throws Exception {
        Path input = tempDir.resolve("v5a_result.csv");
        Files.writeString(
                input,
                "caseId,jobCategoryMiddle,jobCategorySmall,aiScore,aiJobFit,aiImpact,aiCompleteness,aiFeedback,aiMissingKeywordsJson,aiQuestionAnalysesJson,rawLlmResponseJson,errorMessage,createdAt\n"
                        + csv(caseId) + ",중분류,소분류,80,80,80,80,"
                        + csv("피드백") + ","
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson()) + ",,"
                        + csv("2026-07-23T00:00:00") + "\n",
                StandardCharsets.UTF_8
        );
        return input;
    }

    private String rawLlmResponseJson() throws Exception {
        return objectMapper.writeValueAsString(new AnalysisLlmResponse(
                70,
                60,
                65,
                "피드백",
                List.of(new AnalysisLlmResponse.HighlightItem("강점", "좋은 문장")),
                List.of(),
                List.of(),
                List.of()
        ));
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
