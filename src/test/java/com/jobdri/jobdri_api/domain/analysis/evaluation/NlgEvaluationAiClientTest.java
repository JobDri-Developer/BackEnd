package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NlgEvaluationAiClientTest {

    @Test
    @DisplayName("NLG judge 프롬프트는 G-Eval 절차와 구조화 출력만 요구한다")
    void buildPromptContainsJudgeRulesWithoutChainOfThoughtOutput() {
        NlgEvaluationAiClient client = new NlgEvaluationAiClient(
                mock(OpenAIClient.class),
                mock(LlmConcurrencyLimiter.class)
        );

        String prompt = client.buildPrompt(new NlgEvaluationAiClient.NlgJudgeInput(
                "EV-01",
                "evaluation/result.csv",
                "재고 분석",
                "장애 대응 경험",
                "SQL 우대",
                "지원 동기",
                "답변",
                "[]",
                "[]",
                "[]",
                "",
                "",
                "",
                0,
                0,
                List.of()
        ));

        assertThat(prompt).contains("G-Eval 기반 NLG judge");
        assertThat(prompt).contains("상세 chain-of-thought를 출력하지 않는다");
        assertThat(prompt).contains("최종 Structured Output만 반환한다");
        assertThat(prompt).contains("relevance, problemValidity, sentenceTypeConsistency, reasonCorrectness, contextAwareness");
        assertThat(prompt).contains("faithfulness, tenseConsistency, usability, nonMeta, meaningPreservation");
        assertThat(prompt).contains("FALSE_POSITIVE_ANALYSIS");
        assertThat(prompt).contains("UNSUPPORTED_FACT");
        assertThat(prompt).contains("questionAnalyses가 없으면 questionAnalysisEvaluations는 []");
        assertThat(prompt).contains("problemValidity <= 2이면 FALSE_POSITIVE_ANALYSIS");
        assertThat(prompt).contains("낮은 점수가 있는데 NONE만 반환하지 않는다");
        assertThat(prompt).contains("noAnalysisAppropriateness");
        assertThat(prompt).contains("MISSED_ANALYSIS");
        assertThat(prompt).contains("strengthsCoverage");
        assertThat(prompt).contains("missingKeywordsCoverage");
        assertThat(prompt).contains("MISSED_MISSING_KEYWORD");
        assertThat(prompt).contains("actual missingKeywords가 빈 배열이라고 해서 자동으로 정확한 것이 아니다");
        assertThat(prompt).contains("actualMissingKeywordCount");
        assertThat(prompt).contains("validatedMissingKeywordCandidateCount");
        assertThat(prompt).contains("기본값을 4로 두지 말고");
        assertThat(prompt).contains("1,2,3,4,5를 실제 오류 심각도에 따라 분산");
    }

    @Test
    @DisplayName("questionAnalysesJson이 빈 배열이면 평가 배열도 반드시 빈 배열이어야 한다")
    void buildPromptRequiresEmptyQuestionAnalysisEvaluationsForEmptyInputAnalyses() {
        String prompt = buildPrompt(
                "[]",
                "{\"questionAnalysisCandidates\":[{\"sentence\":\"복원 후보\"}]}",
                "{\"reviews\":[{\"accepted\":false}]}"
        );

        assertThat(prompt)
                .contains("questionAnalysisEvaluations MUST be an empty array")
                .contains("Do not infer, reconstruct, create, or restore question analyses")
                .contains("Rejected or removed candidates must not be converted into questionAnalysisEvaluations")
                .contains("삭제되었거나 거절된 candidate를 questionAnalysisEvaluation으로 복원하지 않는다")
                .contains("빈 questionAnalysesJson은 그 자체로 validation error가 아니며")
                .contains("noAnalysisAppropriateness로 평가한다")
                .contains("\"questionAnalysisEvaluations\": []");
    }

    @Test
    @DisplayName("questionAnalysesJson에 2개 분석이 있으면 해당 입력 분석만 평가하도록 지시한다")
    void buildPromptRequiresOneEvaluationPerInputAnalysisOnly() {
        String prompt = buildPrompt(
                """
                        [
                          {"sentence":"첫 번째 분석 문장","status":"MENTIONED"},
                          {"sentence":"두 번째 분석 문장","status":"FABRICATED"}
                        ]
                        """,
                "{}",
                "{}"
        );

        assertThat(prompt)
                .contains("The number of questionAnalysisEvaluations must equal the number of entries in questionAnalysesJson")
                .contains("Each evaluation must correspond to exactly one input question analysis")
                .contains("Do not add evaluations for analyses that are not present in questionAnalysesJson")
                .contains("analysisIndex는 questionAnalysesJson 입력 배열의 index만 사용한다")
                .contains("raw/sanitized/review candidate는 참고 자료일 뿐 평가 대상 분석 목록이 아니다");
    }

    @Test
    @DisplayName("questionAnalysisEvaluations 계약을 추가해도 기존 점수, 오류, case-level 평가 기준은 유지한다")
    void buildPromptKeepsExistingJudgeCriteria() {
        String prompt = buildPrompt("[]", "{}", "{}");

        assertThat(prompt)
                .contains("relevance, problemValidity, sentenceTypeConsistency, reasonCorrectness, contextAwareness")
                .contains("faithfulness, tenseConsistency, usability, nonMeta, meaningPreservation")
                .contains("problemValidity <= 2이면 FALSE_POSITIVE_ANALYSIS")
                .contains("contextAwareness <= 2이면 CONTEXT_IGNORED")
                .contains("nonMeta <= 2이면 META_IMPROVEMENT")
                .contains("다른 오류 코드가 하나라도 있으면 NONE을 함께 반환하지 않는다")
                .contains("noAnalysisAppropriateness")
                .contains("strengthsPrecision")
                .contains("strengthsCoverage")
                .contains("missingKeywordsPrecision")
                .contains("missingKeywordsCoverage")
                .contains("overallUsefulness")
                .contains("caseErrorCodes")
                .contains("shortRationale")
                .contains("MISSED_ANALYSIS")
                .contains("MISSED_MISSING_KEYWORD");
    }

    private String buildPrompt(
            String questionAnalysesJson,
            String rawCandidateResponseJson,
            String candidateReviewResponseJson
    ) {
        NlgEvaluationAiClient client = new NlgEvaluationAiClient(
                mock(OpenAIClient.class),
                mock(LlmConcurrencyLimiter.class)
        );
        return client.buildPrompt(new NlgEvaluationAiClient.NlgJudgeInput(
                "EV-01",
                "evaluation/result.csv",
                "재고 분석",
                "장애 대응 경험",
                "SQL 우대",
                "지원 동기",
                "답변",
                questionAnalysesJson,
                "[]",
                "[]",
                rawCandidateResponseJson,
                "",
                candidateReviewResponseJson,
                0,
                0,
                List.of()
        ));
    }
}
