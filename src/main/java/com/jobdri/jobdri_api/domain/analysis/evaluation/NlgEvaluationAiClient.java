package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
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
        try {
            StructuredResponse<NlgEvaluationResponse> response = llmConcurrencyLimiter.execute(
                    "analysis-nlg-judge",
                    () -> openAIClient.responses().create(params)
            );
            ResponseUsage usage = response.usage().orElse(null);
            return new JudgeCallResult(
                    extractStructuredContent(response),
                    elapsedMillis(startedAt),
                    toIntegerTokenCount(usage == null ? null : usage.inputTokens()),
                    toIntegerTokenCount(usage == null ? null : usage.outputTokens())
            );
        } catch (RuntimeException e) {
            Throwable rootCause = rootCause(e);
            log.error(
                    "NLG Judge call failed. caseId={}, sourceResultFile={}, exceptionType={}, message={}, rootCauseType={}, rootCauseMessage={}, rawJudgeResponseAvailable=false",
                    input.caseId(),
                    input.sourceResultFile(),
                    e.getClass().getName(),
                    e.getMessage(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    e
            );
            throw e;
        }
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
                8. questionAnalyses가 비어 있으면 실제로 첨삭 대상이 없는지, 중요한 문제를 놓친 것인지 평가한다.
                9. strengths와 missingKeywords는 precision뿐 아니라 coverage도 평가한다.
                10. 1~5 점수와 허용 errorCodes만 반환한다.

                [점수 기준]
                - questionAnalysis별: relevance, problemValidity, sentenceTypeConsistency, reasonCorrectness, contextAwareness를 1~5로 평가한다.
                - improvement별: faithfulness, tenseConsistency, usability, nonMeta, meaningPreservation을 1~5로 평가한다.
                - 빈 분석 평가: noAnalysisAppropriateness를 1~5로 평가한다. questionAnalyses가 비어 있고 명백한 문제 문장이 있으면 낮게 평가한다.
                - keyStrengths: strengthsPrecision과 strengthsCoverage를 각각 1~5로 평가한다. 명백한 좋은 문장을 놓치면 MISSED_STRENGTH를 사용한다.
                - missingKeywords: missingKeywordsPrecision과 missingKeywordsCoverage를 각각 1~5로 평가한다. JD 핵심 경험 요구사항 누락을 놓치면 MISSED_MISSING_KEYWORD를 사용한다.
                - actual missingKeywords가 빈 배열이라고 해서 자동으로 정확한 것이 아니다.
                - JD와 answer 기준상 필요한 누락 키워드가 존재하면 actual=[]라도 missingKeywordsCoverage를 낮게 평가하고 MISSED_MISSING_KEYWORD를 사용한다.
                - actual=[]이고 실제로 누락 키워드가 없을 때만 정상 빈 배열로 평가한다.
                - 빈 배열은 precision과 coverage를 분리해 판단한다.
                - case 전체: overallUsefulness를 1~5로 독립 평가한다. 기본값을 4로 두지 말고 1,2,3,4,5 전체 범위를 실제 품질에 맞게 사용한다.
                - 치명적 오류(UNSUPPORTED_FACT, TENSE_CHANGED, FALSE_POSITIVE_ANALYSIS, INVALID_MISSING_KEYWORD)가 있으면 overallUsefulness에 4~5점을 주지 않는다.
                - questionAnalyses가 없으면 questionAnalysisEvaluations는 []로 둔다. 가짜 평가를 생성하지 않는다.
                - questionAnalyses가 비어 있어도 중요한 첨삭 대상을 놓쳤다면 MISSED_ANALYSIS를 부여하고 overallUsefulness를 낮게 평가한다.

                [errorCode와 점수 정합성]
                - problemValidity <= 2이면 FALSE_POSITIVE_ANALYSIS 후보로 검토한다.
                - contextAwareness <= 2이면 CONTEXT_IGNORED 후보로 검토한다.
                - sentenceTypeConsistency <= 2이면 WRONG_SENTENCE_TYPE_CRITERIA 후보로 검토한다.
                - faithfulness <= 2이면 UNSUPPORTED_FACT 후보로 검토한다.
                - tenseConsistency <= 2이면 TENSE_CHANGED 후보로 검토한다.
                - nonMeta <= 2이면 META_IMPROVEMENT 후보로 검토한다.
                - 다른 오류 코드가 하나라도 있으면 NONE을 함께 반환하지 않는다.
                - 모든 핵심 기준이 충분히 양호할 때만 NONE을 반환한다.
                - 낮은 점수가 있는데 NONE만 반환하지 않는다.

                [허용 errorCodes]
                FALSE_POSITIVE_ANALYSIS, CONTEXT_IGNORED, WRONG_SENTENCE_TYPE_CRITERIA, PREFERENCE_OVERWEIGHTED,
                META_IMPROVEMENT, UNSUPPORTED_FACT, TENSE_CHANGED, MEANING_STRENGTHENED,
                INVALID_MISSING_KEYWORD, MISSED_STRENGTH, MISSED_ANALYSIS, MISSED_MISSING_KEYWORD, NONE

                [few-shot 판정 예시]
                - 좋은 분석: 문제 문장이 실제로 부족하고 improvement가 원문 사실만 다듬으면 주요 점수 4~5, errorCodes=[NONE].
                - 좋은 문장 오탐: 이미 수치/방법/결과가 충분한 문장을 MENTIONED로 잡으면 problemValidity=1~2, errorCodes=[FALSE_POSITIVE_ANALYSIS].
                - 문맥 무시: 앞뒤 문장에 근거가 있는데 대상 문장만 보고 부족하다고 하면 contextAwareness=1~2, errorCodes=[CONTEXT_IGNORED].
                - 메타 improvement: "구체적으로 작성했습니다"처럼 첨삭 행위를 설명하면 nonMeta=1~2, errorCodes=[META_IMPROVEMENT].
                - 빈 결과의 false negative: questionAnalyses=[]이지만 답변에 명백한 첨삭 대상이 있으면 noAnalysisAppropriateness=1~2, errorCodes=[MISSED_ANALYSIS].
                - 적절한 빈 결과: 답변에 명백한 문제 문장이 없고 강점/누락 키워드도 적절하면 noAnalysisAppropriateness=4~5, errorCodes=[NONE].
                - 누락 키워드 실패: JD 핵심 경험 요구사항 후보가 있는데 missingKeywordsJson=[]이면 missingKeywordsCoverage=1~2, errorCodes=[MISSED_MISSING_KEYWORD].
                - 누락 키워드 정상 빈 배열: JD 핵심 경험 요구사항이 답변에 충분히 반영되어 missingKeywordsJson=[]이면 MISSED_MISSING_KEYWORD를 사용하지 않는다.
                - 점수 예시는 anchoring용이 아니다. 1,2,3,4,5를 실제 오류 심각도에 따라 분산해서 사용한다.

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
                sanitizedCandidateResponseJson: %s
                candidateReviewResponseJson: %s
                actualMissingKeywordCount: %s
                validatedMissingKeywordCandidateCount: %s
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
                input.sanitizedCandidateResponseJson(),
                input.candidateReviewResponseJson(),
                input.actualMissingKeywordCount(),
                input.validatedMissingKeywordCandidateCount()
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

    private Integer toIntegerTokenCount(Long tokens) {
        if (tokens == null) {
            return null;
        }
        return tokens > Integer.MAX_VALUE ? Integer.MAX_VALUE : tokens.intValue();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
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
            String sanitizedCandidateResponseJson,
            String candidateReviewResponseJson,
            int actualMissingKeywordCount,
            int validatedMissingKeywordCandidateCount,
            List<EvaluationQuestionAnalysisResult> questionAnalyses
    ) {
    }
}
