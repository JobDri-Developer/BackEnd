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
    }
}
