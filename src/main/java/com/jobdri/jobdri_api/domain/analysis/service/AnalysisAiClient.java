package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedJobPostingReference;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedQuestionReference;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
// 자소서 분석에 필요한 프롬프트를 만들고 LLM 호출을 수행하는 클라이언트다.
public class AnalysisAiClient {
    private static final int MAX_REFERENCE_SECTION_LENGTH = 3000;
    private static final int MAX_REFERENCE_FIELD_LENGTH = 300;
    private static final int MAX_CRITERIA_ITEMS = 5;
    private static final String OUTPUT_SCHEMA = """
            [출력 형식]
            {
              "jobFit": 70,
              "impact": 55,
              "completeness": 67,
              "feedback": "한 줄 피드백",
              "missingKeywords": [
                {
                  "keyword": "SQL 활용 경험",
                  "source": "qualification"
                }
              ],
              "questionAnalyses": [
                {
                  "questionId": 1,
                  "sentence": "자소서 답변 안에 실제 존재하는 정확한 부분 문자열",
                  "status": "mentioned",
                  "reason": "문제 이유",
                  "improvement": "사용자가 그대로 붙여 넣을 수 있는 완성된 개선 예시 문장"
                }
              ]
            }
            """;
    private static final String EVALUATION_CRITERIA = """
            [평가 절차]
            1. JD의 주요 업무, 필수 자격요건, 우대사항을 구분한다.
            2. 각 요건에 대응하는 자기소개서 원문 근거를 찾는다.
            3. 근거를 proven, mentioned, missing, fabricated 중 하나로 판정한다.
            4. jobFit, impact, completeness를 각각 평가한다.
            5. 감점 금지 조건과 status 오남용 여부를 확인한다.
            6. 보완이 필요한 원문 문장을 문항당 최대 3개만 추출한다.
            7. JD에는 있지만 자소서에 충분히 드러나지 않은 역량을 missingKeywords로 최대 3개 추출한다.
            8. 지정된 JSON만 반환한다.

            [jobFit 평가 기준]
            JD가 요구하는 역량, 경험, 기술을 자기소개서가 얼마나 증명하는지 평가한다.
            체크 항목:
            - 필수 자격요건 매칭
            - 우대사항 반영
            - 주요 업무 연관성
            - 직무 키워드 활용
            - 암묵적 직무 역량 충족
            점수 구간:
            - 85~100: 필수 자격요건 대부분 proven, 직무 키워드가 풍부하고 업무 경험과 JD가 직접 연결됨
            - 70~84: 주요 자격요건이 증명되고 일부 우대사항도 반영됨
            - 55~69: 자격요건 일부만 증명되고 JD와 간접적으로 연결됨
            - 40~54: 자격요건 증명이 거의 없고 직무 관련성이 낮음
            - 40 미만: JD와 자기소개서가 거의 무관함

            [impact 평가 기준]
            주장을 뒷받침하는 근거가 얼마나 구체적이고 설득력 있는지 평가한다.
            체크 항목:
            - 정량적 성과
            - STAR 구조 활용
            - 주장과 근거의 연결
            - Before-After 비교
            - 차별적 경험
            점수 구간:
            - 85~100: 주요 주장에 정량 성과 또는 구체적 에피소드가 있고 STAR 구조가 명확함
            - 70~84: 핵심 주장 대부분에 근거가 있고 일부 수치와 STAR 구조가 존재함
            - 55~69: 경험은 있으나 근거가 모호하고 수치가 거의 없음
            - 40~54: 대부분 근거 없는 주장과 추상 표현으로 구성됨
            - 40 미만: 구체적 근거가 전혀 없음
            감점 금지:
            - 문제 상황 설명에 수치가 없다는 이유만으로 감점하지 않는다.
            - 과정이나 학습 경험에 정량 지표가 없다는 이유만으로 감점하지 않는다.
            - STAR 구조를 형식적으로 완벽하게 따르지 않는다는 이유만으로 감점하지 않는다.
            단, 전체 맥락과 행동, 결과는 이해 가능해야 한다.

            [completeness 평가 기준]
            질문 적합성, 논리 흐름, 문장 표현 품질을 종합 평가한다.
            체크 항목:
            - 질문 적합성
            - 문단 구조와 흐름
            - 논리적 일관성
            - 문장 가독성
            - 설득력 있는 마무리
            점수 구간:
            - 85~100: 모든 문항에 정확히 답하고 논리 흐름과 마무리가 매우 자연스러움
            - 70~84: 대부분 적절하게 답하고 전반적으로 읽기 좋음
            - 55~69: 일부 동문서답, 논리 비약, 반복 표현 또는 마무리 부족이 있음
            - 40~54: 질문과 답변 불일치가 많고 구조와 논리 문제가 큼
            - 40 미만: 대부분 질문 의도와 무관하거나 미완성임
            """;
    private static final String STATUS_AND_WRITING_RULES = """
            [status 판정 기준]
            - proven: 구체적인 경험, 행동, 기술, 프로젝트, 수치 또는 결과로 역량을 실질적으로 입증함
            - mentioned: 관련 키워드나 경험은 언급했지만 구체적인 근거, 에피소드 또는 결과가 부족함
            - missing: 해당 역량이나 요건을 자기소개서에서 전혀 다루지 않음
            - fabricated: 주장 내용이 앞뒤 맥락과 명백히 충돌하거나 현실적으로 불가능한 수준의 비일관성이 있음

            [status 중요 규칙]
            - 직접적인 증거가 부족해도 관련 경험이 있으면 mentioned로 분류한다.
            - missing은 관련 언급이 전혀 없을 때만 사용한다.
            - fabricated는 단순히 근거가 부족하다는 이유로 사용하지 않는다.
            - fabricated는 명백한 모순이나 비현실적 과장이 있을 때만 사용한다.
            - 구체적인 경험이나 수치가 부족하다는 이유만으로 fabricated를 사용하지 않는다.

            [sentence 규칙]
            - questionAnalyses의 sentence는 반드시 해당 questionId의 answer에 실제 포함된 정확한 substring이어야 한다.
            - 원문에 없는 문장을 생성하지 않는다.
            - sentence를 요약하거나 수정하지 않는다.
            - 원문 매칭이 불확실하면 questionAnalyses에 포함하지 않는다.
            - 분석 대상 문장은 문항당 최대 3개만 반환한다.
            - 동일하거나 거의 동일한 문장을 중복 반환하지 않는다.
            - start/end index는 출력하지 않는다. 서버가 Java String character index 기준으로 계산한다.
            - missing은 원문에 해당 문장이 없을 수 있으므로 sentence를 임의로 만들지 않는다.
            - missing은 questionAnalyses에 억지로 넣지 않는다.

            [missingKeywords 규칙]
            - JD에는 있지만 자소서에 충분히 드러나지 않은 요건이나 역량을 추출한다.
            - questionAnalyses와 분리해서 missingKeywords에만 넣는다.
            - 최대 3개만 반환한다.
            - 누락 키워드가 없으면 null이나 필드 생략이 아니라 빈 배열 []을 반환한다.
            - 우선순위는 자격요건(qualification) > 우대사항(preference) > 주요 업무(mainTask)다.
            - keyword는 단순 단어보다 짧은 역량 문구 형태로 작성한다.
            - 가능하면 JD에 실제 들어간 표현을 유지한다.
            - 중복되거나 유사한 keyword는 하나로 묶고, 대표 문구는 자격요건 표현을 우선한다.
            - source는 qualification, preference, mainTask 중 하나만 사용한다.

            [reason 작성 규칙]
            - 사용자가 왜 해당 문장이 보완 대상인지 이해할 수 있게 작성한다.
            - 가능하면 JD의 어떤 업무, 자격요건, 우대사항과 관련된 문제인지 설명한다.
            - 1~2문장으로 간결하게 작성한다.
            - 사용자의 경험을 부정하거나 비난하지 않는다.
            - "잘못되었다"보다 "근거가 부족하다", "결과가 드러나지 않는다"처럼 진단형 표현을 사용한다.
            - 단순 문법 교정보다 직무 적합성, 구체성, 논리 완성도 관점의 이유를 우선한다.

            [improvement 작성 규칙]
            - improvement는 첨삭 조언이 아니라 사용자가 sentence를 대체해 사용할 수 있는 완성된 한국어 문장이어야 한다.
            - 반드시 한국어 평서문으로 작성한다.
            - "추가하세요", "보완하세요", "수정해주세요", "필요합니다" 같은 지시문을 사용하지 않는다.
            - 사용자가 언급하지 않은 경험, 기술, 도구명, 인원수, 금액, 성과 수치를 임의로 만들지 않는다.
            - 수치가 필요하지만 원문에 없다면 N건, X%%, 약 N시간 같은 빈칸 표현을 사용한다.
            - 원래 경험과 맥락을 최대한 유지한다.
            - 가능하면 행동, 역할, 결과가 드러나도록 개선한다.
            - 너무 길거나 과도하게 화려한 문장으로 만들지 않는다.
            - 금지 표현: %s
            """;

    private final OpenAIClient openAIClient;
    private final CorpusRetrievalService corpusRetrievalService;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel;

    public AnalysisLlmResponse analyze(AnalysisExecutionPayload payload) {
        return analyze(payload.jobPosting(), payload.answeredQuestions(), payload.jobCategoryEvaluationCriteria());
    }

    public AnalysisLlmResponse analyze(JobPosting jobPosting, List<Question> questions) {
        return analyze(jobPosting, questions, null);
    }

    public AnalysisLlmResponse analyze(
            JobPosting jobPosting,
            List<Question> questions,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        RetrievalContext referenceContext = emptyContext();
        try {
            referenceContext = corpusRetrievalService.retrieveForAnalysis(jobPosting, questions);
        } catch (Exception e) {
            log.warn("자소서 분석 retrieval 실패. mock analysis will continue without references. message={}", e.getMessage());
            log.debug("analysis retrieval exception", e);
        }
        var params = ResponseCreateParams.builder()
                .model(analysisModel)
                .input(buildPrompt(jobPosting, questions, referenceContext, jobCategoryEvaluationCriteria))
                .temperature(0.2)
                .text(AnalysisLlmResponse.class)
                .build();

        try {
            StructuredResponse<AnalysisLlmResponse> response = llmConcurrencyLimiter.execute(
                    "cover-letter-analysis",
                    () -> openAIClient.responses().create(params)
            );
            return extractStructuredContent(response);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("자소서 분석 OpenAI API 호출 오류: {}", e.getMessage(), e);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 호출에 실패했습니다."
            );
        }
    }

    public AnalysisLlmResponse analyzeForEvaluation(
            AnalysisPromptInput promptInput,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        var params = ResponseCreateParams.builder()
                .model(analysisModel)
                .input(buildPrompt(promptInput, emptyContext(), jobCategoryEvaluationCriteria))
                .temperature(0.2)
                .text(AnalysisLlmResponse.class)
                .build();

        try {
            StructuredResponse<AnalysisLlmResponse> response = llmConcurrencyLimiter.execute(
                    "cover-letter-analysis-evaluation",
                    () -> openAIClient.responses().create(params)
            );
            return extractStructuredContent(response);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("평가용 자소서 분석 OpenAI API 호출 오류: {}", e.getMessage(), e);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "평가용 자소서 분석 AI 호출에 실패했습니다."
            );
        }
    }

    String buildPrompt(
            JobPosting jobPosting,
            List<Question> questions,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return buildPrompt(
                AnalysisPromptInput.from(jobPosting, questions),
                referenceContext,
                jobCategoryEvaluationCriteria
        );
    }

    String buildPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        String questionText = promptInput.questions().stream()
                .map(question -> """
                        - questionId: %d
                          question: %s
                          answer: %s
                        """.formatted(
                        question.questionId(),
                        defaultString(question.question()),
                        defaultString(question.answer())
                ))
                .reduce("", (left, right) -> left + "\n" + right);

        String similarJobPostingText = formatJobPostingReferences(referenceContext.jobPostingReferences());
        String similarQuestionText = formatQuestionReferences(referenceContext.questionReferences());
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);

        return """
                [시스템 지시]
                너는 한국 채용 담당자이자 자기소개서 평가 전문가다.
                반드시 JSON만 출력한다.
                자소서 원문에 없는 sentence를 만들지 않는다.
                sentence는 반드시 해당 question의 answer에 포함된 정확한 부분 문자열이어야 한다.
                한국어 사용자 노출 라벨을 만들거나 추정하지 않는다.

                %s

                %s

                %s

                [채용 공고]
                회사명: %s
                직무명: %s
                주요 업무:
                %s

                자격 요건:
                %s

                우대 사항:
                %s

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s

                [출력 전 자체 검증]
                - JSON 외 텍스트, 마크다운, 코드블럭을 출력하지 않는다.
                - questionAnalyses의 questionId는 입력된 questionId 중 하나만 사용한다.
                - questionAnalyses의 status는 proven, mentioned, missing, fabricated 중 하나만 사용한다.
                - sentence는 answer에 포함된 정확한 substring만 사용한다.
                - missing 상태를 questionAnalyses에 넣기 위해 원문에 없는 sentence를 만들지 않는다.
                - missingKeywords는 최대 3개이며, 없으면 []로 출력한다.
                - missingKeywords의 source는 qualification, preference, mainTask 중 하나만 사용한다.
                - improvement가 지시문이 아닌 완성된 한국어 평서문인지 확인한다.
                - 원문에 없는 경험, 기술, 도구명, 인원수, 금액, 성과 수치를 만들지 않았는지 확인한다.
                - fabricated를 단순 근거 부족에 사용하지 않았는지 확인한다.
                - jobFit, impact, completeness는 0~100 정수로 출력한다.
                - 총점 score는 서버가 jobFit 50%%, impact 30%%, completeness 20%%로 계산하므로 출력하지 않는다.
                """.formatted(
                OUTPUT_SCHEMA,
                EVALUATION_CRITERIA,
                STATUS_AND_WRITING_RULES.formatted(AnalysisImprovementRules.bannedPhrasesText()),
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                similarJobPostingText,
                similarQuestionText,
                jobCategoryCriteriaSection,
                questionText
        );
    }

    private String formatJobCategoryEvaluationCriteriaSection(JobCategoryEvaluationCriteria criteria) {
        if (criteria == null) {
            return "";
        }

        return """
                [직무별 보조 평가 기준]
                중분류: %s
                주의:
                - 이 직무별 기준은 실제 JD를 대체하지 않는다.
                - 실제 JD의 자격요건, 우대사항, 주요업무를 우선한다.
                - 직무별 기준은 JD가 모호하거나 암묵 역량 판단이 필요할 때만 보조적으로 참고한다.
                - missingKeywords는 실제 JD 표현을 우선 사용하고, 직무별 missingKeywordExamples는 문구 정리와 유사 키워드 묶기에만 참고한다.
                - 직무별 기준에 있는 키워드가 자소서에 없다는 이유만으로 무조건 missing 처리하지 않는다.
                - 원문에 없는 수치, 도구, 경험을 만들어내지 않는다.
                핵심 역량: %s
                관련 행동: %s
                관련 키워드: %s
                좋은 근거 예시: %s
                누락 키워드 문구 예시: %s
                """.formatted(
                defaultString(criteria.jobCategoryMiddle()),
                formatCriteriaList(criteria.coreCompetencies()),
                formatCriteriaList(criteria.relatedActions()),
                formatCriteriaList(criteria.relatedKeywords()),
                truncate(defaultString(criteria.goodEvidenceExample()), MAX_REFERENCE_FIELD_LENGTH),
                formatCriteriaList(criteria.missingKeywordExamples())
        ).trim();
    }

    private String formatCriteriaList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "없음";
        }
        String formatted = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_CRITERIA_ITEMS)
                .map(value -> "- " + truncate(value.trim(), MAX_REFERENCE_FIELD_LENGTH))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
        return formatted.isBlank() ? "없음" : "\n" + formatted;
    }

    private String formatJobPostingReferences(List<RetrievedJobPostingReference> references) {
        if (references == null || references.isEmpty()) {
            return "없음";
        }
        String formatted = references.stream()
                .map(reference -> """
                        - 회사명: %s
                          직무명: %s
                          주요 업무: %s
                          자격 요건: %s
                          우대 사항: %s
                          거리: %.4f
                        """.formatted(
                        truncate(defaultString(reference.companyName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.roleName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.responsibilities()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.requirements()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.preferred()), MAX_REFERENCE_FIELD_LENGTH),
                        reference.distance()
                ))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
        return truncate(formatted, MAX_REFERENCE_SECTION_LENGTH);
    }

    private String formatQuestionReferences(List<RetrievedQuestionReference> references) {
        if (references == null || references.isEmpty()) {
            return "없음";
        }
        String formatted = references.stream()
                .map(reference -> """
                        - 회사명: %s
                          직무명: %s
                          문항 유형: %s
                          글자 수 제한: %s
                          문항: %s
                          거리: %.4f
                        """.formatted(
                        truncate(defaultString(reference.companyName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.roleName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.questionType()), MAX_REFERENCE_FIELD_LENGTH),
                        reference.charLimit() == null ? "" : reference.charLimit(),
                        truncate(defaultString(reference.questionText()), MAX_REFERENCE_FIELD_LENGTH),
                        reference.distance()
                ))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
        return truncate(formatted, MAX_REFERENCE_SECTION_LENGTH);
    }

    private AnalysisLlmResponse extractStructuredContent(StructuredResponse<AnalysisLlmResponse> response) {
        return response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR,
                        "AI 응답에서 자소서 분석 결과를 찾을 수 없습니다."
                ));
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private RetrievalContext emptyContext() {
        return new RetrievalContext(List.of(), List.of());
    }
}
