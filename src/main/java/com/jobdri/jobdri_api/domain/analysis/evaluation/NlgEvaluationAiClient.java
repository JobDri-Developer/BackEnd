package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class NlgEvaluationAiClient {
    private final OpenAIClient openAIClient;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;

    @Value("${evaluation.nlg-judge.model:gpt-4o-mini}")
    private String judgeModel;

    JudgeCallResult evaluate(NlgJudgeInput input) {
        long startedAt = System.nanoTime();
        var params = ResponseCreateParams.builder()
                .model(judgeModel)
                .input(buildPrompt(input))
                .temperature(0.0)
                .text(NlgEvaluationResponse.class)
                .build();
        StructuredResponse<NlgEvaluationResponse> response = llmConcurrencyLimiter.execute(
                "analysis-nlg-judge",
                () -> openAIClient.responses().create(params)
        );
        return new JudgeCallResult(extractStructuredContent(response), elapsedMillis(startedAt), null, null);
    }

    String buildPrompt(NlgJudgeInput input) {
        return """
                [역할]
                너는 자기소개서 분석 결과를 평가하는 G-Eval 기반 NLG judge다.
                상세 chain-of-thought를 출력하지 않는다.
                내부 평가 절차는 따르되 최종 Structured Output만 반환한다.
                shortRationale은 두세 문장 이내의 평가 요약만 작성한다.

                [평가 절차]
                1. JD의 mainTasks, qualifications, preferences를 구분한다.
                2. answer 원문과 questionAnalyses의 sentence exact match를 확인한다.
                3. 각 questionAnalysis가 mainTask 또는 qualification과 직접 관련되는지 확인한다.
                4. preference-only 판단, 문장 유형 기준 오류, 주변 문맥 무시 여부를 확인한다.
                5. improvement가 원문 사실을 보존하고 메타 첨삭 문장이 아닌지 확인한다.
                6. keyStrengths quote가 answer 원문에 있고 좋은 문장인지 확인한다.
                7. missingKeywords가 JD 원문 기반 경험형 키워드인지 확인한다.
                8. 1~5 점수와 허용 errorCodes만 반환한다.

                [점수 기준]
                - questionAnalysis별: relevance, problemValidity, sentenceTypeConsistency, reasonCorrectness, contextAwareness를 1~5로 평가한다.
                - improvement별: faithfulness, tenseConsistency, usability, nonMeta, meaningPreservation을 1~5로 평가한다.
                - case 전체: strengthsPrecision, missingKeywordsPrecision, overallUsefulness를 1~5로 평가한다.
                - questionAnalyses가 없으면 questionAnalysisEvaluations는 []로 둔다. 가짜 평가를 생성하지 않는다.

                [허용 errorCodes]
                FALSE_POSITIVE_ANALYSIS, CONTEXT_IGNORED, WRONG_SENTENCE_TYPE_CRITERIA, PREFERENCE_OVERWEIGHTED,
                META_IMPROVEMENT, UNSUPPORTED_FACT, TENSE_CHANGED, MEANING_STRENGTHENED,
                INVALID_MISSING_KEYWORD, MISSED_STRENGTH, NONE

                [입력]
                caseId: %s
                sourceResultFile: %s
                mainTasks: %s
                qualifications: %s
                preferences: %s
                question: %s
                answer: %s
                questionAnalysesJson: %s
                keyStrengthsJson: %s
                missingKeywordsJson: %s
                rawCandidateResponseJson: %s
                candidateReviewResponseJson: %s
                """.formatted(
                input.caseId(),
                input.sourceResultFile(),
                input.mainTasks(),
                input.qualifications(),
                input.preferences(),
                input.question(),
                input.answer(),
                input.questionAnalysesJson(),
                input.keyStrengthsJson(),
                input.missingKeywordsJson(),
                input.rawCandidateResponseJson(),
                input.candidateReviewResponseJson()
        );
    }

    private NlgEvaluationResponse extractStructuredContent(StructuredResponse<NlgEvaluationResponse> response) {
        return response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NLG judge 응답에서 구조화된 결과를 찾을 수 없습니다."));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    record JudgeCallResult(
            NlgEvaluationResponse response,
            Long latencyMs,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }

    record NlgJudgeInput(
            String caseId,
            String sourceResultFile,
            String mainTasks,
            String qualifications,
            String preferences,
            String question,
            String answer,
            String questionAnalysesJson,
            String keyStrengthsJson,
            String missingKeywordsJson,
            String rawCandidateResponseJson,
            String candidateReviewResponseJson,
            List<EvaluationQuestionAnalysisResult> questionAnalyses
    ) {
    }
}
