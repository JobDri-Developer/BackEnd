package com.jobdri.jobdri_api.domain.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.CandidateReviewResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
// 자소서 분석에 필요한 프롬프트를 만들고 LLM 호출을 수행하는 클라이언트다.
public class AnalysisAiClient {
    private static final int MAX_REFERENCE_SECTION_LENGTH = 3000;
    private static final int MAX_REFERENCE_FIELD_LENGTH = 300;
    private static final int MAX_CRITERIA_ITEMS = 5;
    private static final String OUTPUT_SCHEMA = """
            [출력 규칙]
            - Structured Output 스키마에 맞는 JSON object만 반환한다.
            - jobFit: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수
            - impact: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수
            - completeness: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수
            - feedback: 한 줄 피드백
            - keyStrengths: 없으면 []
            - keyWeaknesses: 없으면 []
            - missingKeywords: 없으면 []
            - questionAnalyses: 실제 첨삭이 필요한 문장에 따라 0~3개
            - improvement: 안전한 개선문을 만들 수 없으면 null
            - 프롬프트 안에 특정 점수 숫자 조합을 JSON 예시로 넣지 않는다.
            - 0, 50, 70, 100 같은 임의 점수도 출력 예시로 사용하지 않는다.
            - Structured Output은 JSON 형식과 타입만 보장한다.
            - sentence 원문 존재 여부, status/reason 정합성, improvement 사실 생성 여부, missingKeyword JD 근거 여부는 서버가 다시 검증한다.
            """;
    private static final String EVALUATION_CRITERIA = """
            [평가 절차]
            최종 JSON을 작성하기 전에 내부적으로 다음 순서를 따른다.
            내부 판단 과정이나 chain-of-thought를 응답에 출력하지 않는다.
            별도의 reasoning 필드나 analysis 필드를 API 응답에 추가하지 않는다.
            모델의 상세 사고과정을 로그나 DB에 저장하지 않는다.
            1. 문장 유형을 경험/성과, 포부/계획, 지원동기, 역량/자격으로 판단한다.
            2. mainTask와 qualification 중 직접 관련된 요구사항을 찾는다.
            3. preference만 근거인 경우 첨삭 대상에서 제외한다.
            4. 문장 유형에 맞는 평가 기준만 적용한다.
            5. 실제로 첨삭이 필요한 문장인지 판단한다.
            6. mentioned 또는 fabricated 상태를 결정한다.
            7. reason이 status 및 문장 유형과 일치하는지 확인한다.
            8. 원문 사실만으로 안전한 improvement를 작성할 수 있는지 확인한다.
            9. 새 경험, 수치, 기술, 계획, 시제 변경, 메타 조언이 없는지 재검사한다.
            10. 최종 JSON만 반환한다.

            [문장 유형 구분]
            - 경험/성과: 과거에 수행한 행동, 역할, 문제 해결, 결과, 수치, 산출물이 드러나는 문장
            - 포부/계획: 입사 후 하겠다, 기여하겠다, 성장하겠다처럼 미래 실행 의지를 말하는 문장
            - 지원동기: 회사/직무를 선택한 이유, 관심, 가치관, 동기를 설명하는 문장
            - 역량/자격: 보유 기술, 자격, 면허, 전공, 경력, 교육 이수 여부를 설명하는 문장

            [포부/계획 문장 평가 규칙]
            - "~하겠습니다", "~되겠습니다", "~기여하겠습니다", "~노력하겠습니다", "~성장하고 싶습니다" 등 미래 시점 문장은 포부/계획으로 우선 판단한다.
            - 포부/계획 문장에는 과거 성과 수치, 과거 결과, Before-After를 요구하지 않는다.
            - 포부/계획은 실행 대상, 실행 방법, 단계, 직무 연결성이 구체적인지 중심으로 판단한다.
            - 포부/계획 문장을 "성과 수치가 부족하다"는 이유로 questionAnalyses에 포함하지 않는다.
            - 포부/계획 문장의 reason에는 "성과 수치가 부족", "정량적 결과가 부족", "Before-After가 부족", "과거 성과가 드러나지 않음"을 사용하지 않는다.
            - 포부가 너무 추상적일 때만 실행 대상, 방법, 단계, 직무 연결성을 보완하도록 reason을 작성한다.

            [문장 유형별 평가 기준]
            - 경험/성과: 과거 수행 행동, 역할, 문제, 결과를 평가한다. 모든 경험에 반드시 수치를 요구하지 않는다.
            - 경험/성과: 과정, 역할, 산출물, 검증 결과 중 하나 이상이 충분히 구체적이면 인정한다.
            - 경험/성과: 이미 수치나 명확한 결과가 있으면 "성과 수치 부족" reason을 생성하지 않는다.
            - 포부/계획: 미래 시점 문장에는 과거 성과 수치나 Before-After를 요구하지 않고 실행 대상, 방법, 단계, 직무 연결성을 평가한다.
            - 지원동기: 회사 또는 직무를 선택한 이유가 개인 경험·관심과 연결되는지 평가하며, 지원동기를 성과 문장처럼 평가하지 않는다.
            - 지원동기: 해당 회사만의 이유가 없는 일반론인지 확인한다.
            - 역량/자격: 보유 기술이나 역량의 실제 사용 맥락을 평가한다.
            - 역량/자격: 자격증이나 전공 자체를 문장 첨삭의 핵심 문제로 삼지 않는다.
            - 역량/자격: 해당 기술을 실제로 어떻게 사용했는지가 있으면 그 근거를 평가한다.

            [JD 반영 우선순위]
            - 판단 우선순위는 mainTask > qualification >>> preference다.
            - mainTask와 qualification은 직무 적합성 판단의 핵심 기준이다.
            - preference는 reason과 점수에 보조적으로만 반영한다.
            - preference만 누락된 경우 questionAnalyses의 첨삭 대상으로 선택하지 않는다.
            - preference만 근거로 jobFit을 크게 감점하지 않는다.

            [점수 산정 규칙]
            - Few-shot 예시는 문장 상태 판정과 출력 형식 참고용이며 점수 예시가 아니다.
            - 점수는 실제 JD와 실제 답변 전체를 기준으로 독립적으로 산정한다.
            - 예시의 수치나 특정 고정 점수를 추론해서 사용하지 않는다.
            - 서로 다른 입력에 동일한 점수를 기계적으로 반복하지 않는다.

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
            - questionAnalyses의 허용 status는 mentioned, fabricated뿐이다.
            - mentioned: 관련 경험이나 의도는 있으나 구체성이 부족한 문장
            - missing: 해당 역량이나 요건을 자기소개서에서 전혀 다루지 않음
            - fabricated: JD 또는 답변 내부의 명시적 사실과 직접 충돌하거나, 지원자가 실제로 하지 않았다고 밝힌 경험을 한 것처럼 주장한 경우

            [status 중요 규칙]
            - PROVEN은 questionAnalyses에 반환하지 않는다.
            - 충분히 좋은 문장은 questionAnalyses에 넣지 않고 keyStrengths로 반환한다.
            - MISSING은 sentence가 없으므로 questionAnalyses에 넣지 않고 missingKeywords로만 반환한다.
            - 직접적인 증거가 부족해도 관련 경험이 있으면 mentioned로 분류한다.
            - missing은 관련 언급이 전혀 없을 때만 사용한다.
            - fabricated는 단순히 근거가 부족하다는 이유로 사용하지 않는다.
            - fabricated는 명시적 사실과 직접 충돌하거나 지원자가 하지 않았다고 밝힌 경험을 한 것처럼 주장한 경우에만 사용한다.
            - status 다양성을 만들기 위해 억지로 fabricated를 생성하지 않는다.
            - 구체적인 경험이나 수치가 부족하다는 이유만으로 fabricated를 사용하지 않는다.

            [sentence 규칙]
            - questionAnalyses의 sentence는 반드시 해당 questionId의 answer에 실제 포함된 정확한 substring이어야 한다.
            - 원문에 없는 문장을 생성하지 않는다.
            - sentence를 요약하거나 수정하지 않는다.
            - 원문 매칭이 불확실하면 questionAnalyses에 포함하지 않는다.
            - 좋은 문장은 questionAnalyses에 넣지 않고 keyStrengths로 반환한다.
            - questionAnalyses는 실제 첨삭이 필요한 문장만 반환한다.
            - questionAnalyses는 문항당 0~3개 반환한다.
            - 항상 1개를 반환할 필요가 없다.
            - 실제로 보완이 필요한 문장만 문항당 최대 3개 반환한다.
            - 실제로 독립적인 문제 문장이 여러 개라면 대표 1개만 선택하지 말고 최대 3개까지 반환한다.
            - 동일한 문제를 반복하는 문장은 하나만 선택한다.
            - 보완할 문장이 없으면 빈 배열 []을 반환한다.
            - 동일하거나 거의 동일한 문장을 중복 반환하지 않는다.
            - start/end index는 출력하지 않는다. 서버가 Java String character index 기준으로 계산한다.
            - missing은 원문에 해당 문장이 없을 수 있으므로 sentence를 임의로 만들지 않는다.
            - missing은 questionAnalyses에 억지로 넣지 않는다.

            [missingKeywords 규칙]
            - 실제 입력 JD의 주요 업무, 자격 요건 원문에 존재하지만 자소서에 충분히 드러나지 않은 경험형 역량만 추출한다.
            - 유사 JD 검색 결과, 직무별 보조 평가 기준, few-shot 예시, 모델의 일반 지식에서 키워드를 생성하지 않는다.
            - JD 원문에 없는 SQL, Python, AWS, 대용량 트래픽 같은 키워드를 생성하지 않는다.
            - 자격증, 면허, 어학성적, 학위, 전공, 경력 연차, 근무 가능 여부, 나이, 국적, 졸업 여부 같은 정형 자격요건은 missingKeywords에 넣지 않는다.
            - preference에만 존재하는 항목은 원칙적으로 missingKeywords에서 제외한다.
            - mainTask와 qualification에서 근거를 찾지 못한 경우 preference만으로 문장을 문제 삼지 않는다.
            - reason에서 preference를 핵심 결격 사유처럼 표현하지 않는다.
            - preference가 없다는 이유만으로 mentioned를 생성하지 않는다.
            - questionAnalyses와 분리해서 missingKeywords에만 넣는다.
            - 최대 3개만 반환한다.
            - 누락 키워드가 없으면 null이나 필드 생략이 아니라 빈 배열 []을 반환한다.
            - 우선순위는 주요 업무(mainTask) > 자격요건(qualification) >>> 우대사항(preference)다.
            - keyword는 단순 단어보다 짧은 역량 문구 형태로 작성한다.
            - 반드시 실제 입력 JD에 들어간 표현을 유지한다.
            - 중복되거나 유사한 keyword는 하나로 묶고, 대표 문구는 자격요건 표현을 우선한다.
            - source는 qualification, preference, mainTask 중 하나만 사용한다.

            [핵심 강점/약점 작성 규칙]
            - keyStrengths와 keyWeaknesses는 각각 최대 3개만 반환한다.
            - title은 화면 카드 제목으로 바로 노출할 짧은 한국어 문장으로 작성한다.
            - quote는 반드시 실제 텍스트에서 가져온 짧은 직접 인용이어야 하며 새로 만들거나 요약하지 않는다.
            - keyStrengths의 quote는 자소서 answer에 실제 포함된 정확한 부분 문자열만 사용한다.
            - 충분히 구체적인 좋은 문장은 keyStrengths 후보로 사용한다.
            - keyStrengths는 mainTask 또는 qualification과 직접 연결된 근거를 우선한다.
            - preference만 충족하는 문장은 핵심 강점으로 과대평가하지 않는다.
            - keyStrengths와 questionAnalyses에 같은 quote/sentence를 동시에 넣지 않는다.
            - keyWeaknesses의 첫 항목들은 missingKeywords와 같은 누락 요건을 다룬다.
            - missingKeywords 기반 keyWeaknesses의 quote는 JD의 주요 업무, 자격 요건, 우대 사항에 실제 포함된 표현을 사용한다.
            - missingKeywords가 없으면 keyWeaknesses는 questionAnalyses의 보완 대상 문장 quote를 우선 사용한다.
            - quote는 너무 길게 붙이지 말고 사용자가 근거를 확인할 수 있는 핵심 구절만 사용한다.
            - 적절한 강점이나 약점이 없으면 null이나 필드 생략이 아니라 빈 배열 []을 반환한다.

            [reason 작성 규칙]
            - 사용자가 왜 해당 문장이 보완 대상인지 이해할 수 있게 작성한다.
            - 가능하면 JD의 어떤 업무, 자격요건, 우대사항과 관련된 문제인지 설명한다.
            - 1~2문장으로 간결하게 작성한다.
            - 사용자의 경험을 부정하거나 비난하지 않는다.
            - "잘못되었다"보다 "근거가 부족하다", "결과가 드러나지 않는다"처럼 진단형 표현을 사용한다.
            - 단순 문법 교정보다 직무 적합성, 구체성, 논리 완성도 관점의 이유를 우선한다.

            [improvement 작성 규칙]
            - improvement는 첨삭 조언이 아니라 사용자가 sentence를 대체해 사용할 수 있는 완성된 한국어 문장이어야 한다.
            - improvement는 사용자가 그대로 교체해 쓸 수 있는 자기소개서 문장이어야 하며 첨삭 행위를 설명하는 메타 문장이 아니어야 한다.
            - 개선이 필요하지 않으면 improvement는 null로 반환한다.
            - 원문 정보만으로 개선문을 만들 수 없으면 improvement는 null로 반환한다.
            - 반드시 한국어 평서문으로 작성한다.
            - "추가하세요", "보완하세요", "수정해주세요", "필요합니다" 같은 지시문을 사용하지 않는다.
            - "구체적으로 작성했습니다", "명확히 설명했습니다" 같은 메타 조언을 improvement로 반환하지 않는다.
            - 원문과 실질적으로 동일한 문장을 improvement로 반환하지 않는다.
            - 답변의 다른 문장을 그대로 복사해 improvement로 반환하지 않는다.
            - JD 요구사항을 지원자가 실제 수행한 경험처럼 생성하지 않는다.
            - 사용자가 언급하지 않은 경험, 기술, 도구명, 인원수, 금액, 성과 수치를 임의로 만들지 않는다.
            - 원문이 과거 경험이면 개선문도 과거 경험을 유지한다.
            - 원문이 포부이면 개선문도 포부를 유지한다.
            - 새로운 경험이나 계획을 추가하지 않는다.
            - 수치가 필요하지만 원문에 없다면 N건, X%%, 약 N시간 같은 빈칸 표현을 사용한다.
            - 원래 경험과 맥락을 최대한 유지한다.
            - 가능하면 행동, 역할, 결과가 드러나도록 개선한다.
            - 너무 길거나 과도하게 화려한 문장으로 만들지 않는다.
            - 금지 표현: %s
            """;

    private final OpenAIClient openAIClient;
    private final CorpusRetrievalService corpusRetrievalService;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;
    private final FewShotPromptProvider fewShotPromptProvider;
    private final ObjectMapper objectMapper;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel;

    @Value("${analysis.two-pass.enabled:false}")
    private boolean twoPassEnabled;

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
        try {
            if (twoPassEnabled) {
                return analyzeTwoPass(
                        AnalysisPromptInput.from(jobPosting, questions),
                        referenceContext,
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis"
                ).response();
            }
            return analyzeSinglePass(
                    AnalysisPromptInput.from(jobPosting, questions),
                    referenceContext,
                    jobCategoryEvaluationCriteria,
                    "cover-letter-analysis"
            ).response();
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
        return analyzeForEvaluationResult(promptInput, jobCategoryEvaluationCriteria).response();
    }

    public AnalysisAiCallResult analyzeForEvaluationResult(
            AnalysisPromptInput promptInput,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        try {
            if (twoPassEnabled) {
                return analyzeTwoPass(
                        promptInput,
                        emptyContext(),
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis-evaluation"
                );
            }
            return analyzeSinglePass(
                    promptInput,
                    emptyContext(),
                    jobCategoryEvaluationCriteria,
                    "cover-letter-analysis-evaluation"
            );
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

    private AnalysisAiCallResult analyzeSinglePass(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            String operationName
    ) {
        long startedAt = System.nanoTime();
        AnalysisLlmResponse response = createStructuredResponse(
                operationName,
                buildPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria),
                AnalysisLlmResponse.class
        );
        return AnalysisAiCallResult.singlePass(response, elapsedMillis(startedAt));
    }

    private AnalysisAiCallResult analyzeTwoPass(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            String operationName
    ) {
        long candidateStartedAt = System.nanoTime();
        AnalysisCandidateResponse rawCandidates = createStructuredResponse(
                operationName + "-candidates",
                buildCandidatePrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria),
                AnalysisCandidateResponse.class
        );
        long candidateLatencyMs = elapsedMillis(candidateStartedAt);
        AnalysisCandidateResponse sanitizedCandidates = sanitizeCandidates(promptInput, rawCandidates);
        log.debug(
                "analysis two-pass candidate result. enabled={}, rawAnalysisCount={}, sanitizedAnalysisCount={}, rawStrengthCount={}, sanitizedStrengthCount={}, rawMissingKeywordCount={}, sanitizedMissingKeywordCount={}, model={}, latencyMs={}",
                true,
                size(rawCandidates == null ? null : rawCandidates.analysisCandidates()),
                size(sanitizedCandidates.analysisCandidates()),
                size(rawCandidates == null ? null : rawCandidates.strengthCandidates()),
                size(sanitizedCandidates.strengthCandidates()),
                size(rawCandidates == null ? null : rawCandidates.missingKeywordCandidates()),
                size(sanitizedCandidates.missingKeywordCandidates()),
                analysisModel,
                candidateLatencyMs
        );

        long finalStartedAt = System.nanoTime();
        CandidateReviewResponse reviewResponse = createStructuredResponse(
                operationName + "-final",
                buildFinalPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria, sanitizedCandidates),
                CandidateReviewResponse.class
        );
        AnalysisLlmResponse response = buildFinalResponse(promptInput, sanitizedCandidates, reviewResponse);
        long finalLatencyMs = elapsedMillis(finalStartedAt);
        log.debug(
                "analysis two-pass final result. enabled={}, acceptedCandidateCount={}, rejectedCandidateCount={}, finalAnalysisCount={}, model={}, latencyMs={}",
                true,
                acceptedDecisionCount(reviewResponse),
                rejectedDecisionCount(reviewResponse),
                size(response == null ? null : response.questionAnalyses()),
                analysisModel,
                finalLatencyMs
        );
        return AnalysisAiCallResult.twoPass(response, rawCandidates, sanitizedCandidates, reviewResponse, candidateLatencyMs, finalLatencyMs);
    }

    private <T> T createStructuredResponse(String operationName, String prompt, Class<T> responseType) {
        var params = ResponseCreateParams.builder()
                .model(analysisModel)
                .input(prompt)
                .temperature(0.2)
                .text(responseType)
                .build();
        StructuredResponse<T> response = llmConcurrencyLimiter.execute(
                operationName,
                () -> openAIClient.responses().create(params)
        );
        return extractStructuredContent(response);
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
        return buildSinglePassPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria);
    }

    String buildCandidatePrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        String questionText = formatQuestions(promptInput);
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);
        return """
                [시스템 지시]
                너는 자기소개서 분석을 위한 1차 후보 판정기다.
                반드시 Structured Output 스키마에 맞는 JSON object만 반환한다.
                내부 판단 과정이나 chain-of-thought를 출력하지 않는다.
                reasoning, analysis 같은 별도 필드를 추가하지 않는다.

                [1차 출력 필드]
                - strengthCandidates: 충분히 구체적이고 mainTask 또는 qualification과 직접 연결된 좋은 문장 후보. 없으면 [].
                - analysisCandidates: 실제 첨삭이 필요한 명확한 문장 후보. status는 MENTIONED 또는 FABRICATED만 사용. 최대 3개.
                - missingKeywordCandidates: sentence가 없는 누락 역량 후보. 없으면 [].
                - 점수 필드, feedback, improvement, keyWeaknesses는 1차 출력에 존재하지 않는다.
                - analysisCandidates.candidateId는 각 후보마다 고유한 문자열로 반드시 채운다.
                - analysisCandidates.contextBefore/contextAfter는 sentence 앞뒤 문맥을 짧게 넣는다. 서버가 원문 기준으로 다시 검증한다.

                [허용값]
                - sentenceType: EXPERIENCE, PLAN, MOTIVATION, COMPETENCY
                - relatedSource: MAIN_TASK, QUALIFICATION, PREFERENCE, NONE
                - status: MENTIONED, FABRICATED
                - issueType: LACK_OF_ACTION, LACK_OF_METHOD, LACK_OF_RESULT, LACK_OF_ROLE, LACK_OF_JOB_CONNECTION, ABSTRACT_PLAN, GENERIC_MOTIVATION, UNSUPPORTED_CLAIM, DIRECT_CONTRADICTION

                [1차 판정 절차]
                1. 답변을 문장 단위로 확인한다.
                2. 각 문장의 유형을 분류한다.
                3. mainTask와 qualification의 직접 근거를 찾는다.
                4. preference만 근거인 후보는 제외한다.
                5. 문장이 이미 충분히 구체적인지 판단한다.
                6. 충분한 문장은 strengthCandidates로 분류한다.
                7. 보완이 필요한 문장만 analysisCandidates로 분류한다.
                8. 실제 충돌이 있는 경우에만 FABRICATED를 사용한다.
                9. 누락 요구사항은 sentence가 아니라 missingKeywordCandidates로 분리한다.
                10. 대표 1개만 기계적으로 고르지 말고, 독립적인 문제가 있으면 최대 3개까지 반환한다.

                [1차 후보 규칙]
                - 후보 수보다 정확도를 우선한다.
                - 애매한 문장은 후보로 만들지 않는다.
                - 각 문장을 앞뒤 문맥과 함께 판단한다.
                - 문장 자체 또는 주변 문맥에 충분한 근거가 있으면 제외한다.
                - 대부분의 답변에서 후보가 0개 또는 1개일 수 있다.
                - 2개는 서로 다른 명확한 문제가 있을 때만 허용한다.
                - 3개는 독립적이고 중대한 문제가 정확히 3개 있을 때만 허용한다.
                - 개수를 채우기 위해 후보를 생성하지 않는다.
                - 대표 문장 하나만 기계적으로 고르지도 않는다.
                - questionId는 입력된 questionId 중 하나만 사용한다.
                - sentence와 quote는 해당 answer에 실제 포함된 정확한 부분 문자열만 사용한다.
                - preference-only 후보는 strengthCandidates, analysisCandidates, missingKeywordCandidates에서 제외한다.
                - 충분히 좋은 문장은 analysisCandidates에 넣지 않는다.
                - 포부/계획 문장에는 과거 성과 수치나 Before-After를 요구하지 않는다.
                - 지원동기는 수치 부족으로 분류하지 않는다.
                - MISSING은 analysisCandidates에 넣지 않고 missingKeywordCandidates로만 분리한다.
                - FABRICATED는 JD 또는 답변 내부의 명시적 사실과 직접 충돌하거나, 지원자가 실제로 하지 않았다고 밝힌 경험을 한 것처럼 주장한 경우에만 사용한다.
                - status 다양성이나 개수를 채우기 위해 후보를 만들지 않는다.
                - improvement를 생성하지 않는다.

                [문장 유형별 후보 기준]
                - EXPERIENCE: 역할, 행동, 방법, 결과, 직무 연결성 중 실제로 부족한 요소가 있어야 한다.
                - EXPERIENCE: 주변 문장에 역할, 방법, 성과가 이어지면 부족하다고 판단하지 않는다.
                - PLAN: 과거 성과나 수치를 요구하지 않고 실행 대상, 방법, 단계, 직무 연결성만 본다.
                - MOTIVATION: 수치를 요구하지 않고 회사·직무 선택 이유와 개인 경험의 연결성을 본다.
                - COMPETENCY: 자격증 또는 역량 언급 자체를 문제로 보지 않고 실제 활용 맥락이 필요한 경우에만 후보화한다.
                - FABRICATED는 명시적 사실 충돌이 있을 때만 사용한다. 단순 근거 부족은 MENTIONED다.

                [1차 few-shot 후보 개수 분포]
                - 예시 A: 충분히 구체적인 성과 문장은 strengthCandidates 1개, analysisCandidates 0개.
                - 예시 B: 주변 문맥에 방법과 결과가 이어지면 analysisCandidates 0개.
                - 예시 C: 추상적인 포부 문장 1개만 명확하면 analysisCandidates 1개.
                - 예시 D: 서로 다른 명확한 문제가 2개일 때만 analysisCandidates 2개.
                - 3개 후보 예시는 사용하지 않는다.

                [채용 공고]
                회사명: %s
                직무명: %s
                <main_tasks>
                %s
                </main_tasks>
                <qualifications>
                %s
                </qualifications>
                <preferences role="secondary_only">
                %s
                </preferences>

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s
                """.formatted(
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                formatJobPostingReferences(referenceContext.jobPostingReferences()),
                formatQuestionReferences(referenceContext.questionReferences()),
                jobCategoryCriteriaSection,
                questionText
        );
    }

    String buildFinalPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            AnalysisCandidateResponse candidates
    ) {
        String questionText = formatQuestions(promptInput);
        String candidateJson = writeJson(candidates);
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);
        return """
                [시스템 지시]
                너는 한국 채용 담당자이자 자기소개서 평가 전문가다.
                반드시 Structured Output 스키마에 맞는 JSON object만 반환한다.
                내부 판단 과정이나 chain-of-thought를 출력하지 않는다.

                %s

                [2차 출력 필드]
                - decisions: 검증된 analysisCandidates 각각에 대해 승인 또는 거절 결정을 반환한다.
                - strengths: 검증된 strengthCandidates 범위 안에서 최종 keyStrengths 후보를 반환한다.
                - missingKeywords: 검증된 missingKeywordCandidates 범위 안에서 최종 missingKeywords 후보를 반환한다.
                - jobFit, impact, completeness, feedback을 함께 반환한다.
                - 기존 최종 AnalysisLlmResponse를 직접 만들지 않는다. 서버가 accepted decision만 최종 questionAnalyses로 변환한다.

                [rejectionCode 허용값]
                - ALREADY_SPECIFIC
                - CONTEXT_PROVIDES_EVIDENCE
                - WRONG_SENTENCE_TYPE_CRITERIA
                - PREFERENCE_ONLY
                - NOT_JOB_RELEVANT
                - DUPLICATE_ISSUE
                - UNSUPPORTED_JUDGMENT
                - NOT_ACTIONABLE
                - INVALID_SOURCE
                - NONE

                [2차 후보 검증 절차]
                1. sentence가 원문에 존재하는지 확인한다.
                2. 앞뒤 문맥을 확인한다.
                3. mainTask 또는 qualification과 직접 관련 있는지 확인한다.
                4. preference-only인지 확인한다.
                5. sentenceType이 맞는지 확인한다.
                6. 해당 문장 유형에 맞는 평가 기준이 적용됐는지 확인한다.
                7. 주변 문맥에 이미 역할·방법·성과가 제공되는지 확인한다.
                8. 실제로 사용자에게 첨삭 가치가 있는지 확인한다.
                9. 승인 또는 거절 결정한다.
                10. 승인된 후보에 대해서만 reason과 improvement를 작성한다.
                - 내부 판단 과정은 출력하지 않는다.

                [decision 정합성]
                - accepted=true이면 rejectionCode=NONE, status는 MENTIONED 또는 FABRICATED, reason은 사용자 노출 가능한 최종 사유다.
                - accepted=false이면 rejectionCode는 NONE이 아니어야 하고, improvement는 반드시 null이다.
                - accepted=false의 reason은 내부 거절 근거이며 최종 API에는 노출되지 않는다.
                - 후보가 이미 충분하면 ALREADY_SPECIFIC으로 거절한다.
                - 주변 문맥이 근거를 제공하면 CONTEXT_PROVIDES_EVIDENCE로 거절한다.
                - 문장 유형에 맞지 않는 기준이 적용됐으면 WRONG_SENTENCE_TYPE_CRITERIA로 거절한다.
                - 1차에 없는 새로운 questionAnalysis를 임의로 추가하지 않는다.

                [문장 유형별 첨삭]
                - 경험/성과: 행동, 역할, 방법, 결과 중 실제 부족한 요소만 보완한다.
                - 경험/성과: 이미 수치와 방법이 있으면 부족하다고 평가하지 않는다.
                - 포부/계획: 과거 성과 수치를 요구하지 않고 실행 대상, 방법, 단계, 직무 연결성을 중심으로 reason을 작성한다.
                - 지원동기: 수치를 요구하지 않고 회사 또는 직무 선택 이유와 개인 경험의 연결성을 평가한다.
                - 역량/자격: 자격증이나 전공 자체보다 실제 활용 맥락을 평가한다.
                - 역량/자격: 정형 자격요건은 missingKeywords에서 제외한다.

                [improvement 안전 규칙]
                - 사용자가 그대로 교체해 쓸 수 있는 자기소개서 문장이어야 한다.
                - 첨삭 행위를 설명하는 메타 문장을 금지한다.
                - 원문과 실질적으로 동일한 문장을 금지한다.
                - 답변의 다른 문장 복사를 금지한다.
                - 원문에 없는 수치, 도구, 경험, 직무 수행, 계획 추가를 금지한다.
                - JD 문구를 지원자의 실제 경험처럼 변환하지 않는다.
                - 과거 문장을 미래 포부로 변경하지 않는다.
                - 미래 포부를 과거 경험으로 변경하지 않는다.
                - 원문 사실만으로 안전한 개선이 불가능하면 null을 반환한다.
                - 금지 예시: 구체적으로 설명했습니다. 경험을 추가하면 좋겠습니다. 성과를 강조했습니다. 구체적인 계획을 추가하겠습니다. 명확하게 작성할 수 있습니다.
                - 금지 패턴: 추가해 보, 추가하면 좋, 설명해 보, 구체적으로 작성, 구체적으로 설명, 강조하겠, 추가하겠, 명확히 작성, 작성할 수 있, 설명할 수 있, 보완하겠, 드러내겠, 제시하겠.

                [keyStrengths 복구 규칙]
                - 검증된 strengthCandidates 안에서 strengths를 구성한다.
                - 충분히 구체적이고 JD와 직접 연결된 문장을 strengthCandidates에서 적극 활용한다.
                - 수치, 도구, 역할, 결과가 구체적인 문장은 문제 후보보다 strength 후보를 우선한다.
                - quote는 답변 원문 exact substring이어야 한다.
                - mainTask 또는 qualification 연결을 우선한다.
                - preference-only 강점은 제외한다.
                - 최소 개수를 강제하지 않고 없으면 []를 반환한다.

                [missingKeywords 복구 규칙]
                - 검증된 missingKeywordCandidates 범위 안에서만 missingKeywords를 정리한다.
                - questionAnalyses 후보 개수와 독립적으로 판단한다.
                - mainTask와 qualification만 사용하고 preference는 제외한다.
                - 정형 자격요건은 제외하고 경험형 요구사항은 유지 가능하다.
                - 새 keyword를 임의 생성하지 않는다.

                [점수 산정]
                - 1차 후보 개수와 점수를 연결하지 않는다.
                - questionAnalyses가 0개여도 무조건 고득점으로 처리하지 않는다.
                - questionAnalyses가 많다고 기계적으로 감점하지 않는다.
                - 점수는 JD 전체와 답변 전체를 기준으로 독립적으로 산정한다.
                - Few-shot의 점수나 고정 숫자 예시는 사용하지 않는다.

                [채용 공고]
                회사명: %s
                직무명: %s
                <main_tasks>
                %s
                </main_tasks>
                <qualifications>
                %s
                </qualifications>
                <preferences role="secondary_only">
                %s
                </preferences>

                [검증된 1차 후보]
                %s

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s
                """.formatted(
                OUTPUT_SCHEMA,
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                candidateJson,
                formatJobPostingReferences(referenceContext.jobPostingReferences()),
                formatQuestionReferences(referenceContext.questionReferences()),
                jobCategoryCriteriaSection,
                questionText
        );
    }

    String buildSinglePassPrompt(
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
                .collect(Collectors.joining("\n"));

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

                [Few-shot 예시]
                %s

                [채용 공고]
                회사명: %s
                직무명: %s
                아래 JD 영역의 역할:
                - <main_tasks>는 최우선 업무 기준이다.
                - <qualifications>는 핵심 자격요건 기준이다.
                - <preferences role="secondary_only">는 보조 기준이며 단독 결격 사유로 사용하지 않는다.

                <main_tasks>
                %s
                </main_tasks>

                <qualifications>
                %s
                </qualifications>

                <preferences role="secondary_only">
                %s
                </preferences>

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
                - keyStrengths와 keyWeaknesses는 각각 최대 3개이며, 없으면 []로 출력한다.
                - keyStrengths의 quote는 answer에 실제 포함된 substring만 사용한다.
                - keyWeaknesses에서 missingKeywords를 다루는 항목의 quote는 실제 JD 문구만 사용한다.
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
                fewShotPromptProvider.getPrompt(),
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

    AnalysisCandidateResponse sanitizeCandidates(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse candidates
    ) {
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));
        List<AnalysisCandidateResponse.StrengthCandidate> strengthCandidates = sanitizeStrengthCandidates(candidates, answerByQuestionId);
        List<AnalysisCandidateResponse.AnalysisCandidate> analysisCandidates = sanitizeAnalysisCandidates(candidates, answerByQuestionId);
        List<AnalysisCandidateResponse.MissingKeywordCandidate> missingKeywordCandidates = sanitizeMissingKeywordCandidates(promptInput, candidates);
        return new AnalysisCandidateResponse(strengthCandidates, analysisCandidates, missingKeywordCandidates);
    }

    private List<AnalysisCandidateResponse.StrengthCandidate> sanitizeStrengthCandidates(
            AnalysisCandidateResponse candidates,
            Map<Long, String> answerByQuestionId
    ) {
        if (candidates == null || candidates.strengthCandidates() == null) {
            return List.of();
        }
        List<AnalysisCandidateResponse.StrengthCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AnalysisCandidateResponse.StrengthCandidate candidate : candidates.strengthCandidates()) {
            if (candidate == null || candidate.questionId() == null || !StringUtils.hasText(candidate.quote())) {
                continue;
            }
            String answer = answerByQuestionId.get(candidate.questionId());
            if (!containsExact(answer, candidate.quote()) || !isPrimarySource(candidate.relatedSource())) {
                continue;
            }
            String dedupeKey = candidate.questionId() + ":" + normalize(candidate.quote());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            result.add(candidate);
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private List<AnalysisCandidateResponse.AnalysisCandidate> sanitizeAnalysisCandidates(
            AnalysisCandidateResponse candidates,
            Map<Long, String> answerByQuestionId
    ) {
        if (candidates == null || candidates.analysisCandidates() == null) {
            return List.of();
        }
        List<AnalysisCandidateResponse.AnalysisCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AnalysisCandidateResponse.AnalysisCandidate candidate : candidates.analysisCandidates()) {
            if (candidate == null || candidate.questionId() == null || !StringUtils.hasText(candidate.sentence())) {
                continue;
            }
            String answer = answerByQuestionId.get(candidate.questionId());
            if (!containsExact(answer, candidate.sentence()) || !isPrimarySource(candidate.relatedSource())) {
                continue;
            }
            QuestionAnalysisStatus status = parseQuestionAnalysisStatus(candidate.status());
            if (status != QuestionAnalysisStatus.MENTIONED && status != QuestionAnalysisStatus.FABRICATED) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !AnalysisSanitizationRules.hasFabricatedDirectConflictReason(candidate.reasonBasis())) {
                continue;
            }
            if (!StringUtils.hasText(candidate.candidateId())) {
                continue;
            }
            String dedupeKey = candidate.questionId() + ":" + normalize(candidate.sentence());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            result.add(new AnalysisCandidateResponse.AnalysisCandidate(
                    candidate.candidateId().trim(),
                    candidate.questionId(),
                    candidate.sentence(),
                    contextBefore(answer, candidate.sentence()),
                    contextAfter(answer, candidate.sentence()),
                    candidate.sentenceType(),
                    candidate.relatedSource(),
                    candidate.relatedRequirement(),
                    candidate.status(),
                    candidate.issueType(),
                    candidate.reasonBasis()
            ));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private List<AnalysisCandidateResponse.MissingKeywordCandidate> sanitizeMissingKeywordCandidates(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse candidates
    ) {
        if (candidates == null || candidates.missingKeywordCandidates() == null) {
            return List.of();
        }
        List<AnalysisCandidateResponse.MissingKeywordCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AnalysisCandidateResponse.MissingKeywordCandidate candidate : candidates.missingKeywordCandidates()) {
            if (candidate == null || !StringUtils.hasText(candidate.keyword())) {
                continue;
            }
            Optional<MissingKeywordSource> source = parseCandidateSource(candidate.source());
            if (source.isEmpty()
                    || !AnalysisSanitizationRules.isValidMissingKeyword(
                    candidate.keyword(),
                    source.get(),
                    promptInput.mainTasks(),
                    promptInput.qualifications()
            )) {
                continue;
            }
            String dedupeKey = normalize(candidate.keyword());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            result.add(candidate);
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private Optional<MissingKeywordSource> parseCandidateSource(String source) {
        if ("MAIN_TASK".equalsIgnoreCase(defaultString(source))) {
            return Optional.of(MissingKeywordSource.MAIN_TASK);
        }
        if ("QUALIFICATION".equalsIgnoreCase(defaultString(source))) {
            return Optional.of(MissingKeywordSource.QUALIFICATION);
        }
        if ("PREFERENCE".equalsIgnoreCase(defaultString(source))) {
            return Optional.of(MissingKeywordSource.PREFERENCE);
        }
        return MissingKeywordSource.from(source);
    }

    AnalysisLlmResponse buildFinalResponse(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse
    ) {
        List<AnalysisLlmResponse.QuestionAnalysisItem> questionAnalyses = buildAcceptedQuestionAnalyses(
                promptInput,
                sanitizedCandidates,
                reviewResponse
        );
        List<AnalysisLlmResponse.HighlightItem> keyStrengths = buildFinalStrengths(
                sanitizedCandidates,
                reviewResponse,
                questionAnalyses
        );
        List<AnalysisLlmResponse.MissingKeywordItem> missingKeywords = buildFinalMissingKeywords(
                promptInput,
                sanitizedCandidates,
                reviewResponse
        );
        return new AnalysisLlmResponse(
                reviewResponse == null ? null : reviewResponse.jobFit(),
                reviewResponse == null ? null : reviewResponse.impact(),
                reviewResponse == null ? null : reviewResponse.completeness(),
                reviewResponse == null ? null : reviewResponse.feedback(),
                keyStrengths,
                List.of(),
                missingKeywords,
                questionAnalyses
        );
    }

    private List<AnalysisLlmResponse.QuestionAnalysisItem> buildAcceptedQuestionAnalyses(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse
    ) {
        if (reviewResponse == null || reviewResponse.decisions() == null || sanitizedCandidates == null) {
            return List.of();
        }
        Map<String, AnalysisCandidateResponse.AnalysisCandidate> candidateById = sanitizedCandidates.analysisCandidates().stream()
                .filter(candidate -> StringUtils.hasText(candidate.candidateId()))
                .collect(Collectors.toMap(
                        candidate -> candidate.candidateId().trim(),
                        candidate -> candidate,
                        (left, right) -> left
                ));
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));

        List<AnalysisLlmResponse.QuestionAnalysisItem> result = new ArrayList<>();
        Set<String> seenCandidateIds = new HashSet<>();
        for (CandidateReviewResponse.CandidateDecision decision : reviewResponse.decisions()) {
            if (decision == null || !StringUtils.hasText(decision.candidateId())) {
                continue;
            }
            String candidateId = decision.candidateId().trim();
            if (!seenCandidateIds.add(candidateId)) {
                continue;
            }
            AnalysisCandidateResponse.AnalysisCandidate candidate = candidateById.get(candidateId);
            if (candidate == null || !Boolean.TRUE.equals(decision.accepted())) {
                continue;
            }
            if (!"NONE".equalsIgnoreCase(defaultString(decision.rejectionCode()))) {
                continue;
            }
            QuestionAnalysisStatus status = parseQuestionAnalysisStatus(decision.status());
            if (status != QuestionAnalysisStatus.MENTIONED && status != QuestionAnalysisStatus.FABRICATED) {
                continue;
            }
            if (!StringUtils.hasText(decision.reason())) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !AnalysisSanitizationRules.hasFabricatedDirectConflictReason(decision.reason())) {
                continue;
            }
            String answer = answerByQuestionId.get(candidate.questionId());
            if (!containsExact(answer, candidate.sentence())) {
                continue;
            }
            String improvement = AnalysisSanitizationRules.normalizeImprovement(
                    candidate.sentence(),
                    answer,
                    decision.improvement(),
                    false
            );
            result.add(new AnalysisLlmResponse.QuestionAnalysisItem(
                    candidate.questionId(),
                    candidate.sentence(),
                    status.name().toLowerCase(),
                    decision.reason().trim(),
                    StringUtils.hasText(improvement) ? improvement : null
            ));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private List<AnalysisLlmResponse.HighlightItem> buildFinalStrengths(
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse,
            List<AnalysisLlmResponse.QuestionAnalysisItem> questionAnalyses
    ) {
        if (reviewResponse == null || reviewResponse.strengths() == null || sanitizedCandidates == null) {
            return List.of();
        }
        Set<String> allowedQuotes = sanitizedCandidates.strengthCandidates().stream()
                .filter(candidate -> isPrimarySource(candidate.relatedSource()))
                .map(candidate -> normalize(candidate.quote()))
                .collect(Collectors.toSet());
        Set<String> analysisSentences = questionAnalyses.stream()
                .map(item -> normalize(item.sentence()))
                .collect(Collectors.toSet());
        List<AnalysisLlmResponse.HighlightItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateReviewResponse.FinalStrengthCandidate strength : reviewResponse.strengths()) {
            if (strength == null || !StringUtils.hasText(strength.title()) || !StringUtils.hasText(strength.quote())) {
                continue;
            }
            String normalizedQuote = normalize(strength.quote());
            if (!allowedQuotes.contains(normalizedQuote) || analysisSentences.contains(normalizedQuote) || !seen.add(normalizedQuote)) {
                continue;
            }
            result.add(new AnalysisLlmResponse.HighlightItem(strength.title().trim(), strength.quote().trim()));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private List<AnalysisLlmResponse.MissingKeywordItem> buildFinalMissingKeywords(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse
    ) {
        if (reviewResponse == null || reviewResponse.missingKeywords() == null || sanitizedCandidates == null) {
            return List.of();
        }
        Set<String> allowed = sanitizedCandidates.missingKeywordCandidates().stream()
                .map(candidate -> normalize(candidate.keyword()) + ":" + parseCandidateSource(candidate.source())
                        .map(MissingKeywordSource::value)
                        .orElse(""))
                .collect(Collectors.toSet());
        List<AnalysisLlmResponse.MissingKeywordItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateReviewResponse.FinalMissingKeywordCandidate keyword : reviewResponse.missingKeywords()) {
            if (keyword == null || !StringUtils.hasText(keyword.keyword())) {
                continue;
            }
            Optional<MissingKeywordSource> source = parseCandidateSource(keyword.source());
            if (source.isEmpty()) {
                continue;
            }
            String allowedKey = normalize(keyword.keyword()) + ":" + source.get().value();
            if (!allowed.contains(allowedKey)
                    || !AnalysisSanitizationRules.isValidMissingKeyword(
                    keyword.keyword(),
                    source.get(),
                    promptInput.mainTasks(),
                    promptInput.qualifications()
            )) {
                continue;
            }
            String dedupeKey = normalize(keyword.keyword());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            result.add(new AnalysisLlmResponse.MissingKeywordItem(keyword.keyword().trim(), source.get().value()));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private int acceptedDecisionCount(CandidateReviewResponse reviewResponse) {
        if (reviewResponse == null || reviewResponse.decisions() == null) {
            return 0;
        }
        return (int) reviewResponse.decisions().stream()
                .filter(decision -> decision != null && Boolean.TRUE.equals(decision.accepted()))
                .count();
    }

    private int rejectedDecisionCount(CandidateReviewResponse reviewResponse) {
        if (reviewResponse == null || reviewResponse.decisions() == null) {
            return 0;
        }
        return (int) reviewResponse.decisions().stream()
                .filter(decision -> decision != null && !Boolean.TRUE.equals(decision.accepted()))
                .count();
    }

    private String contextBefore(String answer, String sentence) {
        int start = StringUtils.hasText(answer) && StringUtils.hasText(sentence) ? answer.indexOf(sentence) : -1;
        if (start <= 0) {
            return "";
        }
        int previousEnd = Math.max(answer.lastIndexOf('.', start - 1), answer.lastIndexOf('。', start - 1));
        int from = previousEnd < 0 ? 0 : previousEnd + 1;
        return answer.substring(from, start).trim();
    }

    private String contextAfter(String answer, String sentence) {
        int start = StringUtils.hasText(answer) && StringUtils.hasText(sentence) ? answer.indexOf(sentence) : -1;
        if (start < 0) {
            return "";
        }
        int from = start + sentence.length();
        if (from >= answer.length()) {
            return "";
        }
        int nextEnd = answer.indexOf('.', from);
        if (nextEnd < 0) {
            nextEnd = answer.length();
        }
        return answer.substring(from, Math.min(answer.length(), nextEnd + 1)).trim();
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
                .collect(Collectors.joining("\n"));
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

    private String formatQuestions(AnalysisPromptInput promptInput) {
        return promptInput.questions().stream()
                .map(question -> """
                        - questionId: %d
                          question: %s
                          answer: %s
                        """.formatted(
                        question.questionId(),
                        defaultString(question.question()),
                        defaultString(question.answer())
                ))
                .collect(Collectors.joining("\n"));
    }

    private <T> T extractStructuredContent(StructuredResponse<T> response) {
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

    private boolean isPrimarySource(String relatedSource) {
        return "MAIN_TASK".equalsIgnoreCase(defaultString(relatedSource))
                || "QUALIFICATION".equalsIgnoreCase(defaultString(relatedSource));
    }

    private boolean containsExact(String source, String part) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(part)
                && source.contains(part);
    }

    private QuestionAnalysisStatus parseQuestionAnalysisStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return QuestionAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
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

    public record AnalysisAiCallResult(
            AnalysisLlmResponse response,
            AnalysisCandidateResponse rawCandidateResponse,
            AnalysisCandidateResponse sanitizedCandidateResponse,
            CandidateReviewResponse candidateReviewResponse,
            boolean twoPassEnabled,
            long candidateCallLatencyMs,
            long finalCallLatencyMs
    ) {
        static AnalysisAiCallResult singlePass(AnalysisLlmResponse response, long latencyMs) {
            return new AnalysisAiCallResult(response, null, null, null, false, 0, latencyMs);
        }

        static AnalysisAiCallResult twoPass(
                AnalysisLlmResponse response,
                AnalysisCandidateResponse rawCandidateResponse,
                AnalysisCandidateResponse sanitizedCandidateResponse,
                CandidateReviewResponse candidateReviewResponse,
                long candidateCallLatencyMs,
                long finalCallLatencyMs
        ) {
            return new AnalysisAiCallResult(
                    response,
                    rawCandidateResponse,
                    sanitizedCandidateResponse,
                    candidateReviewResponse,
                    true,
                    candidateCallLatencyMs,
                    finalCallLatencyMs
            );
        }
    }
}
