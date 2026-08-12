package com.jobdri.jobdri_api.domain.evaluation.analysis;

import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class NlgEvaluationAiClient {
    private static final String MISSED_ANALYSIS_RULES = """
            [MISSED_ANALYSIS 판정 기준]
            - MISSED_ANALYSIS는 다음을 모두 만족할 때만 caseErrorCodes에 포함한다.
              1. answer에 독립적으로 식별 가능한 명확한 문제 문장 또는 핵심 구절이 존재한다.
              2. 그 문제는 단순한 "더 구체적이면 좋음" 수준이 아니다.
              3. 문제 유형을 특정할 수 있다. 예: 근거 없는 과장, 명시적 모순, 문항 의도 불충족, 핵심 행동/역할/방법/결과의 명확한 부재.
              4. 해당 문장을 분석하지 않아 사용자에게 실질적인 첨삭 가치 손실이 발생한다.
              5. 현재 questionAnalyses에 동일하거나 충분히 유사한 문제 분석이 존재하지 않는다.
            - 다음 경우에는 MISSED_ANALYSIS를 부여하지 않는다.
              문장이 더 좋아질 수 있다는 정도, 수치가 없다는 이유만 있는 경우, 추상적인 포부지만 문항 목적상 자연스러운 경우,
              이미 다른 분석이 동일한 핵심 문제를 다루는 경우, 명확한 문제 문장을 특정할 수 없는 경우,
              답변 전반에 대한 일반적 아쉬움만 있는 경우, 단순히 분석 개수가 적다는 이유,
              "구체성 부족"만 있고 어느 문장이 왜 문제인지 특정할 수 없는 경우.
            - MISSED_ANALYSIS를 부여할 때 shortRationale에는 놓친 원문 문장 또는 핵심 구절, 문제 유형, 기존 분석으로 커버되지 않는 이유를 간결하게 포함한다.
            - 이 근거를 제시할 수 없으면 MISSED_ANALYSIS를 부여하지 않는다.
            - 명확한 문제 문장이 없다고 판단했다면 MISSED_ANALYSIS를 errorCodes에 포함하지 않는다.
            - "명확한 문제 문장이 없다"와 "MISSED_ANALYSIS"를 동시에 주장하지 않는다.
            - "첨삭 대상이 명확하지 않다"와 "중요한 첨삭 대상을 놓쳤다"를 동시에 주장하지 않는다.
            """;

    private static final String MISSED_MISSING_KEYWORD_RULES = """
            [MISSED_MISSING_KEYWORD 판정 기준]
            - MISSED_MISSING_KEYWORD는 다음을 모두 만족할 때만 caseErrorCodes에 포함한다.
              1. 실제 JD 원문의 mainTasks 또는 qualifications에 요구사항이 존재한다.
              2. JobDri 서비스 정책상 missing keyword 제외 대상이 아니다.
              3. answer에 동일하거나 의미상 충족되는 내용이 없다.
              4. 현재 missingKeywords에 동일하거나 충분히 유사한 키워드가 없다.
            - MISSED_MISSING_KEYWORD를 부여한다면 missedMissingKeywordEvaluations에도 같은 개수 이상의 근거를 작성한다.
            - missedMissingKeywordEvaluations의 각 항목은 keyword, source, relatedRequirement, reason을 모두 포함한다.
            - relatedRequirement는 mainTasks 또는 qualifications에 실제 존재하는 JD 원문 일부를 그대로 사용한다.
            - source는 MAIN_TASK 또는 QUALIFICATION만 사용한다.
            - "JD의 핵심 경험 요구사항", "채용 관련 경험", "업무 경험 부족", "핵심 경험"처럼 추상적인 표현을 keyword나 relatedRequirement로 사용하지 않는다.
            - JD에 없는 요구사항을 생성하지 않는다.
            - missing keyword 평가 대상에서 제외한다: 자격증, 면허, 학력, 경력 연차, 나이, 법적/정형 보유 조건, 선택형 자격요건의 다른 선택지.
            - 제외 예시: 사회복지사, 청소년상담사, 직업상담사, 운전면허, 대졸 이상, 경력 3년 이상.
            - 제외 대상이 아닌 예시: Spring Boot 실무 경험, 포토샵 활용 능력, 엑셀 고급 활용, 더존 사용 능력, 4대보험 신고 경험.
            - source=QUALIFICATION이라는 이유만으로 모든 항목을 제외하지 않는다.
            - JD에 없는 키워드, 다른 직군의 일반 요구사항, 답변에 이미 존재하는 키워드, OR 조건에서 하나를 충족했을 때 나머지 선택지를 누락으로 판단하지 않는다.
            - MISSED_MISSING_KEYWORD를 부여할 때 shortRationale에는 JD의 실제 요구사항, 답변에서 충족되지 않은 이유, 기존 missingKeywords로 커버되지 않는 이유를 간결하게 포함한다.
            - 이 근거를 제시할 수 없으면 MISSED_MISSING_KEYWORD를 부여하지 않는다.
            """;

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
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
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
                8. questionAnalyses가 비어 있으면 명확한 첨삭 대상이 실제로 있는지 엄격하게 확인한다.
                9. strengths와 missingKeywords는 precision뿐 아니라 coverage도 평가하되 JobDri 서비스 정책의 제외 대상을 따른다.
                10. 1~5 점수와 허용 errorCodes만 반환한다.

                [점수 기준]
                - questionAnalysis별: relevance, problemValidity, sentenceTypeConsistency, reasonCorrectness, contextAwareness를 1~5로 평가한다.
                - improvement별: faithfulness, tenseConsistency, usability, nonMeta, meaningPreservation을 1~5로 평가한다.
                - 빈 분석 평가: noAnalysisAppropriateness를 1~5로 평가한다. questionAnalyses가 비어 있고 명확한 문제 문장이 있으면 낮게 평가한다.
                  5점: 명확한 첨삭 대상이 없으므로 0개가 적절함.
                  4점: 아주 사소한 개선 여지는 있으나 분석 없음이 대체로 적절함.
                  3점: 판단이 애매함.
                  2점: 명확한 첨삭 대상이 일부 존재함.
                  1점: 여러 개의 명확한 문제를 놓침.
                - questionAnalyses가 1개 이상이면 noAnalysisAppropriateness는 분석 부재 평가가 아니므로 전체 품질을 대표하지 않는 중립적 보조값으로만 둔다.
                - keyStrengths: strengthsPrecision과 strengthsCoverage를 각각 1~5로 평가한다. 명백한 좋은 문장을 놓치면 MISSED_STRENGTH를 사용한다.
                - missingKeywords: missingKeywordsPrecision과 missingKeywordsCoverage를 각각 1~5로 평가한다. JD 핵심 경험 요구사항 누락을 놓치면 MISSED_MISSING_KEYWORD를 사용한다.
                - actual missingKeywords가 빈 배열이라고 해서 자동으로 정확한 것이 아니다.
                - JD와 answer 기준상 필요한 비정형 업무·역량 누락 키워드가 존재하면 actual=[]라도 missingKeywordsCoverage를 낮게 평가하고 MISSED_MISSING_KEYWORD를 사용한다. 단, 자격증, 면허, 학력, 경력 연차, 나이 등 정형 자격요건은 이후 [MISSED_MISSING_KEYWORD 판정 기준]의 제외 정책이 우선하며 MISSED_MISSING_KEYWORD 대상이 아니다.
                - actual=[]이고 실제로 누락 키워드가 없을 때만 정상 빈 배열로 평가한다.
                - 빈 배열은 precision과 coverage를 분리해 판단한다.
                - case 전체: overallUsefulness를 1~5로 독립 평가한다. 기본값을 3이나 4로 두지 말고 1,2,3,4,5 전체 범위를 실제 품질에 맞게 사용한다.
                  5점: 분석, 강점, 누락 키워드가 대부분 정확하고 사용자가 바로 수정에 활용할 수 있음.
                  4점: 일부 아쉬움은 있으나 핵심 진단과 개선 방향이 유용함.
                  3점: 일부는 유용하지만 중요한 누락 또는 부정확성이 존재하는 혼합 품질.
                  2점: 다수 평가가 부정확하거나 핵심 문제를 놓쳐 활용도가 낮음.
                  1점: 대부분 잘못됐거나 JD/답변과 무관하여 실질적으로 사용할 수 없음.
                - overallUsefulness는 question analysis 정확성, 문제 문장 식별 적절성, reason 정확성, 문맥 반영, strengths precision/coverage, missingKeywords precision/coverage, 서비스 정책 준수를 종합한다.
                - analysisCount가 0이라는 이유만으로 overallUsefulness를 자동으로 3점 처리하지 않는다.
                - 치명적 오류(UNSUPPORTED_FACT, TENSE_CHANGED, FALSE_POSITIVE_ANALYSIS, INVALID_MISSING_KEYWORD)가 있으면 overallUsefulness에 4~5점을 주지 않는다.
                - questionAnalyses가 없으면 questionAnalysisEvaluations는 []로 둔다. 가짜 평가를 생성하지 않는다.
                - questionAnalyses가 비어 있어도 중요한 첨삭 대상을 놓쳤다면 아래 MISSED_ANALYSIS 엄격 기준을 만족할 때만 MISSED_ANALYSIS를 부여하고 overallUsefulness를 낮게 평가한다.

                [questionAnalysisEvaluations 출력 계약]
                - questionAnalysisEvaluations는 questionAnalysesJson에 명시적으로 포함된 분석 항목만 평가한다.
                - The number of questionAnalysisEvaluations must equal the number of entries in questionAnalysesJson.
                - Each evaluation must correspond to exactly one input question analysis.
                - Do not add evaluations for analyses that are not present in questionAnalysesJson.
                - questionAnalysesJson이 빈 배열인 경우 questionAnalysisEvaluations MUST be an empty array.
                - 이 경우에도 noAnalysisAppropriateness, strengthsPrecision, strengthsCoverage, missingKeywordsPrecision, missingKeywordsCoverage, overallUsefulness, caseErrorCodes, shortRationale은 계속 평가한다.
                - questionAnalysesJson이 빈 배열이어도 question, answer, keyStrengthsJson, missingKeywordsJson, rawCandidateResponseJson, sanitizedCandidateResponseJson, candidateReviewResponseJson을 보고 새로운 question analysis를 추론하거나 생성하지 않는다.
                - Do not infer, reconstruct, create, or restore question analyses from question, answer, keyStrengthsJson, missingKeywordsJson, rawCandidateResponseJson, sanitizedCandidateResponseJson, or candidateReviewResponseJson.
                - Rejected or removed candidates must not be converted into questionAnalysisEvaluations.
                - 삭제되었거나 거절된 candidate를 questionAnalysisEvaluation으로 복원하지 않는다.
                - raw/sanitized/review candidate는 참고 자료일 뿐 평가 대상 분석 목록이 아니다.
                - 빈 questionAnalysesJson은 그 자체로 validation error가 아니며, 분석 부재의 적절성은 noAnalysisAppropriateness로 평가한다.
                - 출력 예: questionAnalysesJson=[]이면 반드시 "questionAnalysisEvaluations": [] 이어야 한다.
                - analysisIndex는 questionAnalysesJson 입력 배열의 index만 사용한다.
                - questionAnalysesJson이 빈 배열이면 output questionAnalysisEvaluations도 빈 배열이어야 한다.
                - 입력에 없는 분석 평가를 생성하거나, answer에서 임의 문장을 골라 evaluation 배열을 만들거나, 분석 개수를 0보다 크게 만들지 않는다.

                %s

                %s

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
                - MISSED_ANALYSIS를 부여하지 않는 빈 결과: 구체적인 경험과 직무 연결이 충분하고 일부 문장이 더 자세해질 수는 있지만 명백한 오류나 첨삭 대상이 없으면 questionAnalyses=[], noAnalysisAppropriateness=4~5, MISSED_ANALYSIS 없음.
                - MISSED_ANALYSIS를 부여하는 빈 결과: answer에 "저는 모든 업무를 완벽하게 해낼 수 있습니다."처럼 근거 없는 과장 표현이라는 명확한 문제 문장이 있고 questionAnalyses=[]이면 noAnalysisAppropriateness=1~2, errorCodes=[MISSED_ANALYSIS]. shortRationale에 원문 구절과 문제 유형을 명시한다.
                - 적절한 빈 결과: 답변에 명백한 문제 문장이 없고 강점/누락 키워드도 적절하면 noAnalysisAppropriateness=4~5, errorCodes=[NONE].
                - 정형 자격증 누락 금지: JD가 "사회복지사, 청소년상담사 중 1개 이상"이고 answer에 "청소년상담사 3급 보유"가 있으면 missingKeywordsJson=[]이어도 MISSED_MISSING_KEYWORD 없음. 사회복지사를 누락으로 보지 않는다.
                - JD 밖 키워드 금지: JD가 "엑셀 고급 활용, 4대보험 신고, 더존 사용"인데 채용 파이프라인 소싱, 온보딩 프로그램 기획을 기대하면 안 된다. 실제 JD에 없으므로 MISSED_MISSING_KEYWORD 금지.
                - 실제 누락 키워드 실패: JD에 "Spring Boot 기반 REST API 개발"이 있고 answer에 Java 학습 경험만 있으며 Spring Boot 사용 언급이 없고 missingKeywordsJson=[]이면 missingKeywordsCoverage=1~2, errorCodes=[MISSED_MISSING_KEYWORD] 가능.
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
                MISSED_ANALYSIS_RULES,
                MISSED_MISSING_KEYWORD_RULES,
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
