package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertThat(prompt).contains("기본값을 3이나 4로 두지 말고");
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

    @Test
    @DisplayName("MISSED_ANALYSIS는 명확한 문제 문장과 근거가 있을 때만 허용하도록 지시한다")
    void buildPromptRestrictsMissedAnalysisCriteria() {
        String prompt = buildPrompt("[]", "{}", "{}");

        assertThat(prompt)
                .contains("[MISSED_ANALYSIS 판정 기준]")
                .contains("독립적으로 식별 가능한 명확한 문제 문장 또는 핵심 구절")
                .contains("단순한 \"더 구체적이면 좋음\" 수준이 아니다")
                .contains("문제 유형을 특정할 수 있다")
                .contains("실질적인 첨삭 가치 손실")
                .contains("현재 questionAnalyses에 동일하거나 충분히 유사한 문제 분석이 존재하지 않는다")
                .contains("문장이 더 좋아질 수 있다는 정도")
                .contains("수치가 없다는 이유만 있는 경우")
                .contains("명확한 문제 문장을 특정할 수 없는 경우")
                .contains("\"구체성 부족\"만 있고 어느 문장이 왜 문제인지 특정할 수 없는 경우")
                .contains("놓친 원문 문장 또는 핵심 구절, 문제 유형, 기존 분석으로 커버되지 않는 이유")
                .contains("명확한 문제 문장이 없다고 판단했다면 MISSED_ANALYSIS를 errorCodes에 포함하지 않는다")
                .contains("\"명확한 문제 문장이 없다\"와 \"MISSED_ANALYSIS\"를 동시에 주장하지 않는다")
                .contains("noAnalysisAppropriateness=4~5, MISSED_ANALYSIS 없음")
                .contains("저는 모든 업무를 완벽하게 해낼 수 있습니다.");
    }

    @Test
    @DisplayName("MISSED_MISSING_KEYWORD는 서비스의 정형 자격요건 제외 정책과 JD 근거를 따른다")
    void buildPromptAlignsMissedMissingKeywordWithServicePolicy() {
        String prompt = buildPrompt("[]", "{}", "{}");

        assertThat(prompt)
                .contains("[MISSED_MISSING_KEYWORD 판정 기준]")
                .contains("필요한 비정형 업무·역량 누락 키워드가 존재하면 actual=[]라도")
                .contains("정형 자격요건은 이후 [MISSED_MISSING_KEYWORD 판정 기준]의 제외 정책이 우선")
                .contains("missedMissingKeywordEvaluations에도 같은 개수 이상의 근거를 작성한다")
                .contains("relatedRequirement는 mainTasks 또는 qualifications에 실제 존재하는 JD 원문 일부를 그대로 사용한다")
                .contains("\"JD의 핵심 경험 요구사항\", \"채용 관련 경험\", \"업무 경험 부족\", \"핵심 경험\"")
                .contains("실제 JD 원문의 mainTasks 또는 qualifications에 요구사항이 존재한다")
                .contains("JobDri 서비스 정책상 missing keyword 제외 대상이 아니다")
                .contains("answer에 동일하거나 의미상 충족되는 내용이 없다")
                .contains("현재 missingKeywords에 동일하거나 충분히 유사한 키워드가 없다")
                .contains("자격증, 면허, 학력, 경력 연차, 나이")
                .contains("선택형 자격요건의 다른 선택지")
                .contains("사회복지사")
                .contains("청소년상담사")
                .contains("운전면허")
                .contains("대졸 이상")
                .contains("경력 3년 이상")
                .contains("Spring Boot 실무 경험")
                .contains("포토샵 활용 능력")
                .contains("엑셀 고급 활용")
                .contains("더존 사용 능력")
                .contains("4대보험 신고 경험")
                .contains("JD에 없는 키워드")
                .contains("OR 조건에서 하나를 충족했을 때 나머지 선택지를 누락으로 판단하지 않는다")
                .contains("사회복지사, 청소년상담사 중 1개 이상")
                .contains("채용 파이프라인 소싱, 온보딩 프로그램 기획")
                .contains("Spring Boot 기반 REST API 개발");
    }

    @Test
    @DisplayName("overallUsefulness와 noAnalysisAppropriateness는 1~5 전체 범위를 쓰도록 rubric을 제공한다")
    void buildPromptDefinesUsefulnessAndNoAnalysisRubrics() {
        String prompt = buildPrompt("[]", "{}", "{}");

        assertThat(prompt)
                .contains("5점: 명확한 첨삭 대상이 없으므로 0개가 적절함.")
                .contains("4점: 아주 사소한 개선 여지는 있으나 분석 없음이 대체로 적절함.")
                .contains("3점: 판단이 애매함.")
                .contains("2점: 명확한 첨삭 대상이 일부 존재함.")
                .contains("1점: 여러 개의 명확한 문제를 놓침.")
                .contains("5점: 분석, 강점, 누락 키워드가 대부분 정확하고 사용자가 바로 수정에 활용할 수 있음.")
                .contains("4점: 일부 아쉬움은 있으나 핵심 진단과 개선 방향이 유용함.")
                .contains("3점: 일부는 유용하지만 중요한 누락 또는 부정확성이 존재하는 혼합 품질.")
                .contains("2점: 다수 평가가 부정확하거나 핵심 문제를 놓쳐 활용도가 낮음.")
                .contains("1점: 대부분 잘못됐거나 JD/답변과 무관하여 실질적으로 사용할 수 없음.")
                .contains("analysisCount가 0이라는 이유만으로 overallUsefulness를 자동으로 3점 처리하지 않는다");
    }

    @Test
    @DisplayName("NLG judge evaluate는 limiter 예외를 감싸지 않고 그대로 전파한다")
    void evaluateRethrowsLimiterRuntimeException() {
        OpenAIClient openAIClient = mock(OpenAIClient.class);
        LlmConcurrencyLimiter llmConcurrencyLimiter = mock(LlmConcurrencyLimiter.class);
        NlgEvaluationAiClient client = new NlgEvaluationAiClient(openAIClient, llmConcurrencyLimiter);
        ReflectionTestUtils.setField(client, "judgeModel", "gpt-4o-mini");
        RuntimeException failure = new RuntimeException("limiter failure");
        when(llmConcurrencyLimiter.execute(anyString(), any())).thenThrow(failure);

        assertThatThrownBy(() -> client.evaluate(judgeInput("[]", "", "")))
                .isSameAs(failure);
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
        return client.buildPrompt(judgeInput(
                questionAnalysesJson,
                rawCandidateResponseJson,
                candidateReviewResponseJson
        ));
    }

    private NlgEvaluationAiClient.NlgJudgeInput judgeInput(
            String questionAnalysesJson,
            String rawCandidateResponseJson,
            String candidateReviewResponseJson
    ) {
        return new NlgEvaluationAiClient.NlgJudgeInput(
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
        );
    }
}
