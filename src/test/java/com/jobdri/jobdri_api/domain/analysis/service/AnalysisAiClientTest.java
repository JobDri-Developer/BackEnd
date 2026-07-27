package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.CandidateRecheckResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.CandidateRecheckResponse.RecheckDecision;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.CandidateReviewResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.CandidateReviewResponse.RejectionCode;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.jobdri.jobdri_api.global.metrics.AsyncMetricsRecorder;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisAiClientTest {

    private final AnalysisAiClient analysisAiClient = new AnalysisAiClient(
            mock(OpenAIClient.class),
            mock(CorpusRetrievalService.class),
            mock(LlmConcurrencyLimiter.class),
            new FewShotPromptProvider(),
            mock(AsyncMetricsRecorder.class),
            new ObjectMapper()
    );

    @Test
    @DisplayName("analysis.mode가 없으면 기존 two-pass boolean으로 분석 모드를 해석한다")
    void resolveAnalysisModeFallsBackToTwoPassBoolean() {
        ReflectionTestUtils.setField(analysisAiClient, "analysisMode", "");
        ReflectionTestUtils.setField(analysisAiClient, "twoPassEnabled", false);
        assertThat(analysisAiClient.resolveAnalysisMode()).isEqualTo(AnalysisAiClient.AnalysisMode.SINGLE_PASS);

        ReflectionTestUtils.setField(analysisAiClient, "twoPassEnabled", true);
        assertThat(analysisAiClient.resolveAnalysisMode()).isEqualTo(AnalysisAiClient.AnalysisMode.TWO_PASS);
    }

    @Test
    @DisplayName("analysis.mode가 기존 two-pass boolean보다 우선한다")
    void analysisModePropertyTakesPrecedenceOverTwoPassBoolean() {
        ReflectionTestUtils.setField(analysisAiClient, "twoPassEnabled", true);
        ReflectionTestUtils.setField(analysisAiClient, "analysisMode", "single-pass");
        assertThat(analysisAiClient.resolveAnalysisMode()).isEqualTo(AnalysisAiClient.AnalysisMode.SINGLE_PASS);

        ReflectionTestUtils.setField(analysisAiClient, "analysisMode", "hybrid-exact");
        assertThat(analysisAiClient.resolveAnalysisMode()).isEqualTo(AnalysisAiClient.AnalysisMode.HYBRID_EXACT);
    }

    @Test
    @DisplayName("지원하지 않는 analysis.mode는 명확한 예외를 던진다")
    void unsupportedAnalysisModeFailsFast() {
        ReflectionTestUtils.setField(analysisAiClient, "analysisMode", "unknown");

        assertThatThrownBy(analysisAiClient::resolveAnalysisMode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported analysis mode: unknown")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Hybrid Exact merge는 questionAnalyses와 점수는 single-pass, missingKeywords는 two-pass 전체 결과를 사용한다")
    void mergeHybridExactUsesExplicitSources() {
        AnalysisLlmResponse.QuestionAnalysisItem singleQuestionAnalysis =
                new AnalysisLlmResponse.QuestionAnalysisItem(1L, "Spring Boot API를 개발했습니다.", "MENTIONED", "결과가 부족합니다.", null);
        AnalysisLlmResponse.QuestionAnalysisItem twoPassQuestionAnalysis =
                new AnalysisLlmResponse.QuestionAnalysisItem(1L, "장애 대응 경험이 있습니다.", "MENTIONED", "역할이 부족합니다.", null);
        AnalysisLlmResponse.MissingKeywordItem singleMissingKeyword =
                new AnalysisLlmResponse.MissingKeywordItem("single 누락", "mainTask");
        AnalysisLlmResponse.MissingKeywordItem twoPassMissingKeyword =
                new AnalysisLlmResponse.MissingKeywordItem("장애 대응 경험", "qualification");
        AnalysisLlmResponse.MissingKeywordItem twoPassMissingKeyword2 =
                new AnalysisLlmResponse.MissingKeywordItem("API 운영 경험", "mainTask");
        AnalysisLlmResponse.MissingKeywordItem twoPassMissingKeyword3 =
                new AnalysisLlmResponse.MissingKeywordItem("트러블슈팅 경험", "qualification");
        AnalysisLlmResponse singlePassResponse = new AnalysisLlmResponse(
                80,
                70,
                60,
                "single feedback",
                List.of(new AnalysisLlmResponse.HighlightItem("강점", "Spring Boot API")),
                List.of(new AnalysisLlmResponse.HighlightItem("약점", "결과")),
                List.of(singleMissingKeyword),
                List.of(singleQuestionAnalysis)
        );
        AnalysisLlmResponse twoPassResponse = new AnalysisLlmResponse(
                10,
                20,
                30,
                "two-pass feedback",
                List.of(new AnalysisLlmResponse.HighlightItem("two-pass 강점", "장애 대응")),
                List.of(),
                List.of(twoPassMissingKeyword, twoPassMissingKeyword2, twoPassMissingKeyword3),
                List.of(twoPassQuestionAnalysis)
        );

        AnalysisLlmResponse merged = analysisAiClient.mergeHybridExact(
                singlePassResponse,
                twoPassResponse
        );

        assertThat(merged.jobFit()).isEqualTo(80);
        assertThat(merged.impact()).isEqualTo(70);
        assertThat(merged.completeness()).isEqualTo(60);
        assertThat(merged.feedback()).isEqualTo("single feedback");
        assertThat(merged.keyStrengths()).isEqualTo(singlePassResponse.keyStrengths());
        assertThat(merged.questionAnalyses()).containsExactly(singleQuestionAnalysis);
        assertThat(merged.missingKeywords())
                .containsExactly(twoPassMissingKeyword, twoPassMissingKeyword2, twoPassMissingKeyword3);
        assertThat(merged.missingKeywords()).doesNotContain(singleMissingKeyword);
        assertThat(merged.questionAnalyses()).doesNotContain(twoPassQuestionAnalysis);
    }

    @Test
    @DisplayName("Hybrid Exact merge는 source 응답 리스트를 방어적으로 복사한다")
    void mergeHybridExactCopiesLists() {
        AnalysisLlmResponse.QuestionAnalysisItem singleQuestionAnalysis =
                new AnalysisLlmResponse.QuestionAnalysisItem(1L, "Spring Boot API를 개발했습니다.", "MENTIONED", "결과가 부족합니다.", null);
        AnalysisLlmResponse.MissingKeywordItem twoPassMissingKeyword =
                new AnalysisLlmResponse.MissingKeywordItem("장애 대응 경험", "qualification");
        AnalysisLlmResponse singlePassResponse = new AnalysisLlmResponse(
                80,
                70,
                60,
                "single feedback",
                new java.util.ArrayList<>(List.of(new AnalysisLlmResponse.HighlightItem("강점", "Spring Boot API"))),
                new java.util.ArrayList<>(),
                new java.util.ArrayList<>(),
                new java.util.ArrayList<>(List.of(singleQuestionAnalysis))
        );
        AnalysisLlmResponse twoPassResponse = new AnalysisLlmResponse(
                10,
                20,
                30,
                "two-pass feedback",
                new java.util.ArrayList<>(),
                new java.util.ArrayList<>(),
                new java.util.ArrayList<>(List.of(twoPassMissingKeyword)),
                new java.util.ArrayList<>()
        );

        AnalysisLlmResponse merged = analysisAiClient.mergeHybridExact(singlePassResponse, twoPassResponse);

        assertThat(merged.keyStrengths()).containsExactlyElementsOf(singlePassResponse.keyStrengths());
        assertThat(merged.questionAnalyses()).containsExactly(singleQuestionAnalysis);
        assertThat(merged.missingKeywords()).containsExactly(twoPassMissingKeyword);
        assertThatThrownBy(() -> merged.questionAnalyses().add(singleQuestionAnalysis))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> merged.missingKeywords().add(twoPassMissingKeyword))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("직무 중분류 기준이 있으면 프롬프트에 보조 평가 기준 섹션을 포함한다")
    void buildPromptIncludesJobCategoryCriteriaWhenPresent() {
        String prompt = analysisAiClient.buildPrompt(
                mockJobPosting(),
                List.of(mockQuestion()),
                new RetrievalContext(List.of(), List.of()),
                new JobCategoryEvaluationCriteria(
                        "AI·개발·데이터",
                        List.of("백엔드 개발"),
                        List.of("트러블슈팅"),
                        List.of("소프트웨어 아키텍처 설계 및 시스템 개발"),
                        List.of("요구사항 분석을 통한 효율적인 개발 및 리팩토링 수행"),
                        List.of("신규 서비스/플랫폼 개발 및 운영 프로젝트 경험"),
                        List.of("API", "데이터베이스 설계"),
                        "시스템 구조 상의 병목을 진단하고 최적화해 안정성을 향상시킨 경험",
                        List.of("API 설계 및 고도화", "시스템 트러블슈팅 경험")
                )
        );

        assertThat(prompt).contains("[직무별 보조 평가 기준]");
        assertThat(prompt).contains("중분류: AI·개발·데이터");
        assertThat(prompt).contains("이 직무별 기준은 실제 JD를 대체하지 않는다.");
        assertThat(prompt).contains("실제 JD의 자격요건, 우대사항, 주요업무를 우선한다.");
        assertThat(prompt).contains("직무별 기준에 있는 키워드가 자소서에 없다는 이유만으로 무조건 missing 처리하지 않는다.");
        assertThat(prompt).contains("소프트웨어 아키텍처 설계 및 시스템 개발");
        assertThat(prompt).contains("API 설계 및 고도화");
    }

    @Test
    @DisplayName("직무 중분류 기준이 없으면 보조 평가 기준 섹션을 생략한다")
    void buildPromptOmitsJobCategoryCriteriaSectionWhenCriteriaMissing() {
        String prompt = analysisAiClient.buildPrompt(
                mockJobPosting(),
                List.of(mockQuestion()),
                new RetrievalContext(List.of(), List.of()),
                null
        );

        assertThat(prompt).doesNotContain("[직무별 보조 평가 기준]");
        assertThat(prompt).doesNotContain("이 직무별 기준은 실제 JD를 대체하지 않는다.");
    }

    @Test
    @DisplayName("프롬프트는 포부 문장, JD 우선순위, missingKeywords, improvement 안전 규칙을 포함한다")
    void buildPromptIncludesReviewPolicyRules() {
        String prompt = analysisAiClient.buildPrompt(
                mockJobPosting(),
                List.of(mockQuestion()),
                new RetrievalContext(List.of(), List.of()),
                null
        );

        assertThat(prompt)
                .contains("[출력 규칙]")
                .contains("Structured Output 스키마에 맞는 JSON object만 반환한다.")
                .contains("jobFit: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수")
                .contains("impact: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수")
                .contains("completeness: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수")
                .contains("questionAnalyses: 실제 첨삭이 필요한 문장에 따라 0~3개")
                .contains("improvement: 안전한 개선문을 만들 수 없으면 null")
                .contains("[문장 유형 구분]")
                .contains("경험/성과")
                .contains("포부/계획")
                .contains("지원동기")
                .contains("역량/자격")
                .contains("포부/계획 문장에는 과거 성과 수치, 과거 결과, Before-After를 요구하지 않는다.")
                .contains("포부/계획은 실행 대상, 실행 방법, 단계, 직무 연결성이 구체적인지 중심으로 판단한다.")
                .contains("판단 우선순위는 mainTask > qualification >>> preference다.")
                .contains("Few-shot 예시는 문장 상태 판정과 출력 형식 참고용이며 점수 예시가 아니다.")
                .contains("점수는 실제 JD와 실제 답변 전체를 기준으로 독립적으로 산정한다.")
                .contains("서로 다른 입력에 동일한 점수를 기계적으로 반복하지 않는다.")
                .contains("preference만 누락된 경우 questionAnalyses의 첨삭 대상으로 선택하지 않는다.")
                .contains("preference는 reason과 점수에 보조적으로만 반영한다.")
                .contains("실제 입력 JD의 주요 업무, 자격 요건 원문에 존재하지만 자소서에 충분히 드러나지 않은 경험형 역량만 추출한다.")
                .contains("유사 JD 검색 결과, 직무별 보조 평가 기준, few-shot 예시, 모델의 일반 지식에서 키워드를 생성하지 않는다.")
                .contains("자격증, 면허, 어학성적, 학위, 전공, 경력 연차, 근무 가능 여부")
                .contains("좋은 문장은 questionAnalyses에 넣지 않고 keyStrengths로 반환한다.")
                .contains("개선이 필요하지 않으면 improvement는 null로 반환한다.")
                .contains("원문 정보만으로 개선문을 만들 수 없으면 improvement는 null로 반환한다.")
                .contains("원문과 실질적으로 동일한 문장을 improvement로 반환하지 않는다.")
                .contains("답변의 다른 문장을 그대로 복사해 improvement로 반환하지 않는다.")
                .contains("메타 조언을 improvement로 반환하지 않는다.")
                .contains("JD 요구사항을 지원자가 실제 수행한 경험처럼 생성하지 않는다.");

        assertThat(prompt)
                .doesNotContain("[출력 형식]")
                .doesNotContain("\"jobFit\"")
                .doesNotContain("\"impact\"")
                .doesNotContain("\"completeness\"");
    }

    @Test
    @DisplayName("프롬프트는 v4 few-shot 편향 완화 규칙과 JD 영역 분리를 포함한다")
    void buildPromptIncludesFewShotV4Rules() {
        String prompt = analysisAiClient.buildPrompt(
                mockJobPosting(),
                List.of(mockQuestion()),
                new RetrievalContext(List.of(), List.of()),
                null
        );

        assertThat(prompt)
                .contains("[Few-shot 예시]")
                .contains("questionAnalyses\": []")
                .contains("status\": \"mentioned\"")
                .contains("status\": \"fabricated\"")
                .contains("충분히 구체적이므로 questionAnalyses에는 포함하지 않는다.")
                .contains("예시의 분석 개수, 상태 비율, 문장 표현, 점수를 실제 입력에 복사하지 않는다.")
                .contains("questionAnalyses는 실제 첨삭이 필요한 문장에 따라 0~3개가 될 수 있다.")
                .contains("항상 1개를 반환할 필요가 없다.")
                .contains("Few-shot 출력에는 전체 점수를 포함하지 않는다.")
                .contains("포부/계획 문장의 reason에는 \"성과 수치가 부족\"")
                .contains("preference가 없다는 이유만으로 mentioned를 생성하지 않는다.")
                .contains("questionAnalyses의 허용 status는 mentioned, fabricated뿐이다.")
                .contains("PROVEN은 questionAnalyses에 반환하지 않는다.")
                .contains("MISSING은 sentence가 없으므로 questionAnalyses에 넣지 않고 missingKeywords로만 반환한다.")
                .contains("실제로 독립적인 문제 문장이 여러 개라면 대표 1개만 선택하지 말고 최대 3개까지 반환한다.")
                .contains("내부 판단 과정이나 chain-of-thought를 응답에 출력하지 않는다.")
                .contains("원문이 과거 경험이면 개선문도 과거 경험을 유지한다.")
                .contains("원문이 포부이면 개선문도 포부를 유지한다.")
                .contains("<main_tasks>")
                .contains("<qualifications>")
                .contains("<preferences role=\"secondary_only\">");

        assertThat(prompt.indexOf("[Few-shot 예시]"))
                .isLessThan(prompt.indexOf("[채용 공고]"));
        assertThat(prompt.indexOf("[Few-shot 예시]"))
                .isLessThan(prompt.indexOf("[자소서 문항과 답변]"));
    }

    @Test
    @DisplayName("1차 후보 프롬프트는 점수와 improvement 없이 후보 판정 규칙만 포함한다")
    void buildCandidatePromptIncludesCandidateRulesOnly() {
        String prompt = analysisAiClient.buildCandidatePrompt(
                promptInput(),
                new RetrievalContext(List.of(), List.of()),
                null
        );

        assertThat(prompt)
                .contains("[1차 출력 필드]")
                .contains("strengthCandidates")
                .contains("analysisCandidates")
                .contains("missingKeywordCandidates")
                .contains("sentenceType: EXPERIENCE, PLAN, MOTIVATION, COMPETENCY")
                .contains("preference만 근거인 후보는 제외한다.")
                .contains("충분한 문장은 strengthCandidates로 분류한다.")
                .contains("보완이 필요한 문장만 analysisCandidates로 분류한다.")
                .contains("MISSING은 analysisCandidates에 넣지 않고 missingKeywordCandidates로만 분리한다.")
                .contains("독립적인 문제가 있으면 최대 3개까지 반환한다.")
                .contains("내부 판단 과정이나 chain-of-thought를 출력하지 않는다.")
                .contains("점수 필드, feedback, improvement, keyWeaknesses는 1차 출력에 존재하지 않는다.");
    }

    @Test
    @DisplayName("후보 sanitizer는 1차 후보의 원문, source, status, 개수를 검증한다")
    void sanitizeCandidatesFiltersInvalidCandidateItems() {
        AnalysisPromptInput promptInput = promptInput();
        AnalysisCandidateResponse sanitized = analysisAiClient.sanitizeCandidates(
                promptInput,
                new AnalysisCandidateResponse(
                        List.of(
                                new AnalysisCandidateResponse.StrengthCandidate(
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "직접 관련됩니다."
                                ),
                                new AnalysisCandidateResponse.StrengthCandidate(
                                        1L,
                                        "답변에 없는 강점",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "원문에 없습니다."
                                )
                        ),
                        List.of(
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "bad-question",
                                        99L,
                                        "Spring Boot API를 개발했습니다.",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "잘못된 questionId입니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "missing-sentence",
                                        1L,
                                        "답변에 없는 문장",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "원문에 없습니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "preference-only",
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "PREFERENCE",
                                        "대용량 트래픽",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "preference-only입니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "bad-status",
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "PROVEN",
                                        "LACK_OF_RESULT",
                                        "허용하지 않은 status입니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "fabricated-no-reason",
                                        1L,
                                        "장애 대응 경험이 있습니다.",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "QUALIFICATION",
                                        "장애 대응 경험",
                                        "FABRICATED",
                                        "DIRECT_CONTRADICTION",
                                        ""
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "fabricated-valid",
                                        1L,
                                        "장애 대응 경험이 있습니다.",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "QUALIFICATION",
                                        "장애 대응 경험",
                                        "FABRICATED",
                                        "DIRECT_CONTRADICTION",
                                        "답변 내부의 명시적 사실과 직접 충돌합니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        "mentioned-valid",
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
                                        "",
                                        "",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "결과가 부족합니다."
                                )
                        ),
                        List.of(
                                new AnalysisCandidateResponse.MissingKeywordCandidate(
                                        "SQL 활용 경험",
                                        "QUALIFICATION",
                                        "Spring Boot 경험"
                                ),
                                new AnalysisCandidateResponse.MissingKeywordCandidate(
                                        "영어 공인성적",
                                        "QUALIFICATION",
                                        "영어 공인성적"
                                ),
                                new AnalysisCandidateResponse.MissingKeywordCandidate(
                                        "API 개발 경험",
                                        "MAIN_TASK",
                                        "API 개발"
                                )
                        )
                )
        );

        assertThat(sanitized.strengthCandidates()).hasSize(1);
        assertThat(sanitized.analysisCandidates()).extracting("sentence")
                .containsExactly("장애 대응 경험이 있습니다.", "Spring Boot API를 개발했습니다.");
        assertThat(sanitized.missingKeywordCandidates()).extracting("keyword")
                .containsExactly("API 개발 경험");
    }

    @Test
    @DisplayName("2차 프롬프트는 검증된 후보만 입력하고 새 questionAnalysis 추가를 금지한다")
    void buildFinalPromptUsesSanitizedCandidatesOnly() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(new AnalysisCandidateResponse.StrengthCandidate(
                        1L,
                        "Spring Boot API를 개발했습니다.",
                        "MAIN_TASK",
                        "API 개발",
                        "직접 근거입니다."
                )),
                List.of(new AnalysisCandidateResponse.AnalysisCandidate(
                        "candidate-1",
                        1L,
                        "장애 대응 경험이 있습니다.",
                        "",
                        "",
                        "EXPERIENCE",
                        "QUALIFICATION",
                        "장애 대응 경험",
                        "MENTIONED",
                        "LACK_OF_RESULT",
                        "결과가 부족합니다."
                )),
                List.of()
        );

        String prompt = analysisAiClient.buildFinalPrompt(
                promptInput(),
                new RetrievalContext(List.of(), List.of()),
                null,
                candidates
        );

        assertThat(prompt)
                .contains("[검증된 1차 후보]")
                .contains("1차에 없는 새로운 questionAnalysis를 임의로 추가하지 않는다.")
                .contains("확실하지 않으면 무조건 제거하지 않는다.")
                .contains("[후보 유지 조건]")
                .contains("구체적인 행동, 역할, 성과/결과, 문제 해결 과정, JD 요구 역량 연결, 기여 범위 중 하나가 부족한 경우 유지한다.")
                .contains("[후보 제거 조건]")
                .contains("다짐이나 포부 문장을 성과 문장처럼 잘못 평가한 경우 제거한다.")
                .contains("누락 키워드가 없어도 문장 첨삭은 존재할 수 있다.")
                .contains("누락 키워드가 있어도 문장 자체는 정상일 수 있다.")
                .contains("첨삭 행위를 설명하는 메타 문장을 금지한다.")
                .contains("원문에 없는 수치, 도구, 경험, 직무 수행, 계획 추가를 금지한다.")
                .contains("과거 문장을 미래 포부로 변경하지 않는다.")
                .contains("미래 포부를 과거 경험으로 변경하지 않는다.")
                .contains("원문 사실만으로 안전한 개선이 불가능하면 null을 반환한다.")
                .contains("1차 후보 개수와 점수를 연결하지 않는다.")
                .contains("점수는 JD 전체와 답변 전체를 기준으로 독립적으로 산정한다.")
                .contains("장애 대응 경험이 있습니다.")
                .doesNotContain("답변에 없는 문장");
    }

    @Test
    @DisplayName("재검증 프롬프트는 1차 후보가 모두 제거된 경우 KEEP_BEST_CANDIDATE 판단 기준을 포함한다")
    void buildRecheckPromptIncludesUnderDetectionRules() {
        String prompt = analysisAiClient.buildRecheckPrompt(
                promptInput(),
                new RetrievalContext(List.of(), List.of()),
                null,
                new AnalysisCandidateResponse(
                        List.of(),
                        List.of(candidate("candidate-1", "장애 대응 경험이 있습니다.")),
                        List.of()
                ),
                new CandidateReviewResponse(
                        List.of(new CandidateReviewResponse.CandidateDecision(
                                "candidate-1",
                                false,
                                RejectionCode.NOT_ACTIONABLE,
                                null,
                                "실질 개선이 어렵습니다.",
                                null
                        )),
                        List.of(),
                        List.of(),
                        80,
                        70,
                        60,
                        "피드백"
                )
        );

        assertThat(prompt)
                .contains("1차 후보가 하나 이상 있었지만 2차 검증 후 accepted 후보가 0개다.")
                .contains("1차 후보 중 사용자가 실제로 수정하면 도움이 되는 문장이 정말 하나도 없는가?")
                .contains("NO_CORRECTION_NEEDED")
                .contains("KEEP_BEST_CANDIDATE")
                .contains("단순히 첫 번째 후보를 선택하지 않는다.")
                .contains("problemClarity, jobRelevance, evidenceGap, improvementUsefulness, fabricationConfidence를 1~5로 내부 평가한다.")
                .contains("status는 MENTIONED 또는 FABRICATED만 사용한다.")
                .contains("안전한 교체 문장을 만들 수 없으면 null이다.");
    }

    @Test
    @DisplayName("2차 review 결과는 accepted 후보만 최종 AnalysisLlmResponse로 변환한다")
    void buildFinalResponseUsesAcceptedCandidateDecisionsOnly() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(new AnalysisCandidateResponse.StrengthCandidate(
                        1L,
                        "Spring Boot API를 개발했습니다.",
                        "MAIN_TASK",
                        "API 개발",
                        "직접 근거입니다."
                )),
                List.of(
                        new AnalysisCandidateResponse.AnalysisCandidate(
                                "accepted-1",
                                1L,
                                "장애 대응 경험이 있습니다.",
                                "Spring Boot API를 개발했습니다.",
                                "",
                                "EXPERIENCE",
                                "QUALIFICATION",
                                "장애 대응 경험",
                                "MENTIONED",
                                "LACK_OF_RESULT",
                                "결과가 부족합니다."
                        ),
                        new AnalysisCandidateResponse.AnalysisCandidate(
                                "rejected-1",
                                1L,
                                "Spring Boot API를 개발했습니다.",
                                "",
                                "장애 대응 경험이 있습니다.",
                                "EXPERIENCE",
                                "MAIN_TASK",
                                "API 개발",
                                "MENTIONED",
                                "LACK_OF_RESULT",
                                "결과가 부족합니다."
                        )
                ),
                List.of(new AnalysisCandidateResponse.MissingKeywordCandidate(
                        "장애 대응 경험",
                        "QUALIFICATION",
                        "장애 대응 경험"
                ))
        );

        AnalysisLlmResponse response = analysisAiClient.buildFinalResponse(
                promptInput(),
                candidates,
                new CandidateReviewResponse(
                        List.of(
                                new CandidateReviewResponse.CandidateDecision(
                                        "accepted-1",
                                        true,
                                        RejectionCode.NONE,
                                        "MENTIONED",
                                        "장애 대응 경험은 언급했지만 결과가 부족합니다.",
                                        "장애 대응 경험이 있습니다."
                                ),
                                new CandidateReviewResponse.CandidateDecision(
                                        "rejected-1",
                                        false,
                                        RejectionCode.ALREADY_SPECIFIC,
                                        null,
                                        "이미 구체적입니다.",
                                        "추가해 보세요."
                                ),
                                new CandidateReviewResponse.CandidateDecision(
                                        "missing-from-first",
                                        true,
                                        RejectionCode.NONE,
                                        "MENTIONED",
                                        "1차에 없는 후보입니다.",
                                        null
                                )
                        ),
                        List.of(new CandidateReviewResponse.FinalStrengthCandidate(
                                "API 개발 경험이 직접 드러나요",
                                "Spring Boot API를 개발했습니다.",
                                "MAIN_TASK"
                        )),
                        List.of(new CandidateReviewResponse.FinalMissingKeywordCandidate(
                                "장애 대응 경험",
                                "QUALIFICATION"
                        )),
                        80,
                        70,
                        60,
                        "검증된 후보 기반 피드백"
                )
        );

        assertThat(response.questionAnalyses()).hasSize(1);
        assertThat(response.questionAnalyses().getFirst().sentence()).isEqualTo("장애 대응 경험이 있습니다.");
        assertThat(response.questionAnalyses().getFirst().improvement()).isNull();
        assertThat(response.keyStrengths()).extracting("quote")
                .containsExactly("Spring Boot API를 개발했습니다.");
        assertThat(response.missingKeywords()).extracting("keyword")
                .containsExactly("장애 대응 경험");
    }

    @Test
    @DisplayName("2차 review가 missingKeywords를 비워도 1차 검증 누락 키워드는 최종 응답에 유지한다")
    void buildFinalResponsePreservesSanitizedMissingKeywordsWhenReviewIsEmpty() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(),
                List.of(
                        new AnalysisCandidateResponse.MissingKeywordCandidate(
                                "Spring Boot 경험",
                                "QUALIFICATION",
                                "Spring Boot 경험"
                        ),
                        new AnalysisCandidateResponse.MissingKeywordCandidate(
                                "API 개발",
                                "MAIN_TASK",
                                "API 개발"
                        )
                )
        );

        AnalysisLlmResponse response = analysisAiClient.buildFinalResponse(
                promptInput(),
                candidates,
                new CandidateReviewResponse(List.of(), List.of(), List.of(), 80, 70, 60, "피드백")
        );

        assertThat(response.questionAnalyses()).isEmpty();
        assertThat(response.missingKeywords())
                .extracting(AnalysisLlmResponse.MissingKeywordItem::keyword)
                .containsExactly("Spring Boot 경험", "API 개발");
    }

    @Test
    @DisplayName("재검증이 KEEP_BEST_CANDIDATE를 반환하면 검증을 통과한 후보 1건만 accepted로 복원한다")
    void applyRecheckResponseKeepsBestCandidateWhenValid() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(
                        candidate("candidate-1", "Spring Boot API를 개발했습니다."),
                        candidate("candidate-2", "장애 대응 경험이 있습니다.")
                ),
                List.of()
        );
        CandidateReviewResponse review = new CandidateReviewResponse(
                List.of(
                        new CandidateReviewResponse.CandidateDecision(
                                "candidate-1",
                                false,
                                RejectionCode.ALREADY_SPECIFIC,
                                null,
                                "이미 구체적입니다.",
                                null
                        ),
                        new CandidateReviewResponse.CandidateDecision(
                                "candidate-2",
                                false,
                                RejectionCode.NOT_ACTIONABLE,
                                null,
                                "수정 가치가 낮습니다.",
                                null
                        )
                ),
                List.of(),
                List.of(),
                80,
                70,
                60,
                "피드백"
        );

        CandidateReviewResponse rechecked = analysisAiClient.applyRecheckResponse(
                promptInput(),
                candidates,
                review,
                new CandidateRecheckResponse(
                        RecheckDecision.KEEP_BEST_CANDIDATE,
                        "candidate-2",
                        "MENTIONED",
                        "장애 대응 경험은 언급했지만 구체적인 역할과 결과가 부족합니다.",
                        "장애 대응 경험에서 제가 맡은 역할을 정리하고 문제 확인 과정과 처리 결과를 기록하며 대응 역량을 키웠습니다.",
                        5,
                        4,
                        4,
                        4,
                        1,
                        true,
                        true,
                        true,
                        true,
                        false
                )
        );

        assertThat(rechecked.decisions()).hasSize(2);
        assertThat(rechecked.decisions().getFirst().candidateId()).isEqualTo("candidate-2");
        assertThat(rechecked.decisions().getFirst().accepted()).isTrue();
        assertThat(rechecked.decisions().getFirst().rejectionCode()).isEqualTo(RejectionCode.NONE);
        assertThat(rechecked.decisions()).extracting("candidateId")
                .containsExactly("candidate-2", "candidate-1");
    }

    @Test
    @DisplayName("재검증이 NO_CORRECTION_NEEDED이거나 점수가 유효하지 않으면 후보를 복원하지 않는다")
    void applyRecheckResponseDoesNotHardCodeFallbackCandidate() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(candidate("candidate-1", "장애 대응 경험이 있습니다.")),
                List.of()
        );
        CandidateReviewResponse review = new CandidateReviewResponse(
                List.of(new CandidateReviewResponse.CandidateDecision(
                        "candidate-1",
                        false,
                        RejectionCode.NOT_ACTIONABLE,
                        null,
                        "수정 가치가 낮습니다.",
                        null
                )),
                List.of(),
                List.of(),
                80,
                70,
                60,
                "피드백"
        );

        CandidateReviewResponse noCorrection = analysisAiClient.applyRecheckResponse(
                promptInput(),
                candidates,
                review,
                new CandidateRecheckResponse(
                        RecheckDecision.NO_CORRECTION_NEEDED,
                        null,
                        null,
                        null,
                        null,
                        1,
                        1,
                        1,
                        1,
                        1,
                        true,
                        true,
                        true,
                        true,
                        false
                )
        );
        CandidateReviewResponse invalidScore = analysisAiClient.applyRecheckResponse(
                promptInput(),
                candidates,
                review,
                new CandidateRecheckResponse(
                        RecheckDecision.KEEP_BEST_CANDIDATE,
                        "candidate-1",
                        "MENTIONED",
                        "장애 대응 경험은 언급했지만 구체적인 역할과 결과가 부족합니다.",
                        null,
                        6,
                        4,
                        4,
                        4,
                        1,
                        true,
                        true,
                        true,
                        true,
                        false
                )
        );

        assertThat(noCorrection.decisions()).hasSize(1);
        assertThat(noCorrection.decisions().getFirst().accepted()).isFalse();
        assertThat(invalidScore.decisions()).hasSize(1);
        assertThat(invalidScore.decisions().getFirst().accepted()).isFalse();
    }

    @Test
    @DisplayName("재검증은 점수 임계값과 boolean 검증 기준을 모두 만족해야 복원한다")
    void applyRecheckResponseRequiresThresholdsAndBooleanFlags() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(candidate("candidate-1", "장애 대응 경험이 있습니다.")),
                List.of()
        );
        CandidateReviewResponse review = rejectedReview("candidate-1");

        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.problemClarity = 3))
                .decisions().getFirst().accepted()).isFalse();
        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.jobRelevance = 3))
                .decisions().getFirst().accepted()).isFalse();
        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.improvementUsefulness = 3))
                .decisions().getFirst().accepted()).isFalse();
        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.questionTypeMatched = false))
                .decisions().getFirst().accepted()).isFalse();
        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.contextConsistent = false))
                .decisions().getFirst().accepted()).isFalse();
        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.reasonSpecific = false))
                .decisions().getFirst().accepted()).isFalse();
        assertThat(applyRecheck(candidates, review, validMentionedRecheck("candidate-1", builder -> builder.improvementActionable = false))
                .decisions().getFirst().accepted()).isFalse();
    }

    @Test
    @DisplayName("재검증은 일반론 reason과 활용성 낮은 improvement를 복원하지 않는다")
    void applyRecheckResponseRejectsGenericReasonAndNonActionableImprovement() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(candidate("candidate-1", "장애 대응 경험이 있습니다.")),
                List.of()
        );
        CandidateReviewResponse review = rejectedReview("candidate-1");

        CandidateReviewResponse genericReason = applyRecheck(
                candidates,
                review,
                validMentionedRecheck("candidate-1", builder -> builder.reason = "구체성이 부족합니다.")
        );
        CandidateReviewResponse styleOnlyImprovement = applyRecheck(
                candidates,
                review,
                validMentionedRecheck("candidate-1", builder -> builder.improvement = "더 구체적으로 작성하겠습니다.")
        );
        CandidateReviewResponse unsupportedNumber = applyRecheck(
                candidates,
                review,
                validMentionedRecheck("candidate-1", builder -> builder.improvement = "장애 대응 경험에서 제가 맡은 역할을 정리하고 30% 개선한 결과를 기록했습니다.")
        );

        assertThat(genericReason.decisions().getFirst().accepted()).isFalse();
        assertThat(styleOnlyImprovement.decisions().getFirst().accepted()).isFalse();
        assertThat(unsupportedNumber.decisions().getFirst().accepted()).isFalse();
    }

    @Test
    @DisplayName("재검증은 문항 유형에 맞지 않는 성과 수치 부족 reason을 복원하지 않는다")
    void applyRecheckResponseRejectsWrongSentenceTypeCriteria() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(new AnalysisCandidateResponse.AnalysisCandidate(
                        "plan-1",
                        1L,
                        "장애 대응 경험이 있습니다.",
                        "",
                        "",
                        "PLAN",
                        "MAIN_TASK",
                        "API 개발",
                        "MENTIONED",
                        "ABSTRACT_PLAN",
                        "포부가 추상적입니다."
                )),
                List.of()
        );

        CandidateReviewResponse rechecked = applyRecheck(
                candidates,
                rejectedReview("plan-1"),
                validMentionedRecheck("plan-1", builder -> builder.reason = "장애 대응 경험은 언급했지만 성과 수치가 부족하여 직무 기준에서 보완이 필요합니다.")
        );

        assertThat(rechecked.decisions().getFirst().accepted()).isFalse();
    }

    @Test
    @DisplayName("재검증은 FABRICATED 직접 충돌 기준을 만족할 때만 복원한다")
    void applyRecheckResponseRequiresDirectContradictionForFabricated() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(new AnalysisCandidateResponse.AnalysisCandidate(
                        "fabricated-1",
                        1L,
                        "장애 대응 경험이 있습니다.",
                        "",
                        "",
                        "EXPERIENCE",
                        "QUALIFICATION",
                        "장애 대응 경험",
                        "FABRICATED",
                        "DIRECT_CONTRADICTION",
                        "답변 내부의 명시적 사실과 직접 충돌합니다."
                )),
                List.of()
        );
        CandidateReviewResponse review = rejectedReview("fabricated-1");

        CandidateReviewResponse invalidFabricated = applyRecheck(
                candidates,
                review,
                validFabricatedRecheck("fabricated-1", builder -> builder.directContradiction = false)
        );
        CandidateReviewResponse lowConfidence = applyRecheck(
                candidates,
                review,
                validFabricatedRecheck("fabricated-1", builder -> builder.fabricationConfidence = 3)
        );
        CandidateReviewResponse validFabricated = applyRecheck(
                candidates,
                review,
                validFabricatedRecheck("fabricated-1", builder -> {
                })
        );

        assertThat(invalidFabricated.decisions().getFirst().accepted()).isFalse();
        assertThat(lowConfidence.decisions().getFirst().accepted()).isFalse();
        assertThat(validFabricated.decisions().getFirst().accepted()).isTrue();
        assertThat(validFabricated.decisions().getFirst().status()).isEqualTo("FABRICATED");
    }

    @Test
    @DisplayName("재검증은 최대 1건만 복원하고 missingKeywords에는 영향 주지 않는다")
    void applyRecheckResponseRecoversAtMostOneAndKeepsMissingKeywords() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(
                        candidate("candidate-1", "Spring Boot API를 개발했습니다."),
                        candidate("candidate-2", "장애 대응 경험이 있습니다.")
                ),
                List.of(new AnalysisCandidateResponse.MissingKeywordCandidate(
                        "장애 대응 경험",
                        "QUALIFICATION",
                        "장애 대응 경험"
                ))
        );
        CandidateReviewResponse review = new CandidateReviewResponse(
                List.of(
                        new CandidateReviewResponse.CandidateDecision("candidate-1", false, RejectionCode.NOT_ACTIONABLE, null, "수정 가치가 낮습니다.", null),
                        new CandidateReviewResponse.CandidateDecision("candidate-2", false, RejectionCode.NOT_ACTIONABLE, null, "수정 가치가 낮습니다.", null)
                ),
                List.of(),
                List.of(new CandidateReviewResponse.FinalMissingKeywordCandidate("장애 대응 경험", "QUALIFICATION")),
                80,
                70,
                60,
                "피드백"
        );

        CandidateReviewResponse rechecked = applyRecheck(
                candidates,
                review,
                validMentionedRecheck("candidate-2", builder -> {
                })
        );

        assertThat(rechecked.decisions()).filteredOn(CandidateReviewResponse.CandidateDecision::accepted).hasSize(1);
        assertThat(rechecked.missingKeywords()).extracting("keyword").containsExactly("장애 대응 경험");
    }

    @Test
    @DisplayName("candidate review는 검증된 candidateId와 accepted/rejectionCode 조합만 유지한다")
    void validateCandidateReviewKeepsOnlyConsistentDecisions() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(
                        candidate("accepted-1", "Spring Boot API를 개발했습니다."),
                        candidate("rejected-1", "장애 대응 경험이 있습니다."),
                        candidate("bad-accepted-code", "Spring Boot API를 개발했습니다."),
                        candidate("bad-rejected-code", "장애 대응 경험이 있습니다.")
                ),
                List.of()
        );

        CandidateReviewResponse validated = analysisAiClient.validateCandidateReview(
                promptInput(),
                candidates,
                new CandidateReviewResponse(
                        List.of(
                                new CandidateReviewResponse.CandidateDecision(
                                        "accepted-1",
                                        true,
                                        RejectionCode.NONE,
                                        "MENTIONED",
                                        "결과가 부족합니다.",
                                        "null"
                                ),
                                new CandidateReviewResponse.CandidateDecision(
                                        "rejected-1",
                                        false,
                                        RejectionCode.CONTEXT_PROVIDES_EVIDENCE,
                                        null,
                                        "주변 문맥이 근거를 제공합니다.",
                                        "추가해 보세요."
                                ),
                                new CandidateReviewResponse.CandidateDecision(
                                        "unknown",
                                        true,
                                        RejectionCode.NONE,
                                        "MENTIONED",
                                        "1차에 없는 후보입니다.",
                                        null
                                ),
                                new CandidateReviewResponse.CandidateDecision(
                                        "bad-accepted-code",
                                        true,
                                        RejectionCode.ALREADY_SPECIFIC,
                                        "MENTIONED",
                                        "accepted=true인데 비NONE입니다.",
                                        null
                                ),
                                new CandidateReviewResponse.CandidateDecision(
                                        "bad-rejected-code",
                                        false,
                                        RejectionCode.NONE,
                                        null,
                                        "accepted=false인데 NONE입니다.",
                                        null
                                )
                        ),
                        List.of(),
                        List.of(),
                        80,
                        70,
                        60,
                        "피드백"
                )
        );

        assertThat(validated.decisions()).hasSize(2);
        assertThat(validated.decisions()).extracting("candidateId")
                .containsExactly("accepted-1", "rejected-1");
        assertThat(validated.decisions().getFirst().improvement()).isNull();
        assertThat(validated.decisions().get(1).improvement()).isNull();
    }

    @Test
    @DisplayName("candidate가 없으면 review decisions는 빈 배열로 검증된다")
    void validateCandidateReviewReturnsEmptyDecisionsWhenNoCandidates() {
        CandidateReviewResponse validated = analysisAiClient.validateCandidateReview(
                promptInput(),
                new AnalysisCandidateResponse(List.of(), List.of(), List.of()),
                new CandidateReviewResponse(
                        List.of(new CandidateReviewResponse.CandidateDecision(
                                "unknown",
                                true,
                                RejectionCode.NONE,
                                "MENTIONED",
                                "1차에 없는 후보입니다.",
                                null
                        )),
                        List.of(),
                        List.of(),
                        80,
                        70,
                        60,
                        "피드백"
                )
        );

        assertThat(validated.decisions()).isEmpty();
    }

    @Test
    @DisplayName("긍정 reason은 MENTIONED 최종 분석으로 변환하지 않는다")
    void buildFinalResponseSkipsPositiveMentionedReason() {
        AnalysisCandidateResponse candidates = new AnalysisCandidateResponse(
                List.of(),
                List.of(candidate("positive-1", "Spring Boot API를 개발했습니다.")),
                List.of()
        );

        CandidateReviewResponse validated = analysisAiClient.validateCandidateReview(
                promptInput(),
                candidates,
                new CandidateReviewResponse(
                        List.of(new CandidateReviewResponse.CandidateDecision(
                                "positive-1",
                                true,
                                RejectionCode.NONE,
                                "MENTIONED",
                                "직무 역량을 보여주는 강점입니다.",
                                null
                        )),
                        List.of(),
                        List.of(),
                        80,
                        70,
                        60,
                        "피드백"
                )
        );

        AnalysisLlmResponse response = analysisAiClient.buildFinalResponse(promptInput(), candidates, validated);

        assertThat(validated.decisions()).isEmpty();
        assertThat(response.questionAnalyses()).isEmpty();
    }

    @Test
    @DisplayName("issueType 값은 rejectionCode enum이 될 수 없다")
    void rejectionCodeDoesNotContainIssueTypeValues() {
        assertThat(RejectionCode.values())
                .extracting(Enum::name)
                .doesNotContain("LACK_OF_RESULT", "DIRECT_CONTRADICTION");
    }

    private CandidateReviewResponse applyRecheck(
            AnalysisCandidateResponse candidates,
            CandidateReviewResponse review,
            CandidateRecheckResponse recheck
    ) {
        return analysisAiClient.applyRecheckResponse(promptInput(), candidates, review, recheck);
    }

    private CandidateReviewResponse rejectedReview(String candidateId) {
        return new CandidateReviewResponse(
                List.of(new CandidateReviewResponse.CandidateDecision(
                        candidateId,
                        false,
                        RejectionCode.NOT_ACTIONABLE,
                        null,
                        "수정 가치가 낮습니다.",
                        null
                )),
                List.of(),
                List.of(),
                80,
                70,
                60,
                "피드백"
        );
    }

    private CandidateRecheckResponse validMentionedRecheck(
            String candidateId,
            Consumer<RecheckBuilder> customizer
    ) {
        RecheckBuilder builder = new RecheckBuilder(candidateId);
        customizer.accept(builder);
        return builder.build();
    }

    private CandidateRecheckResponse validFabricatedRecheck(
            String candidateId,
            Consumer<RecheckBuilder> customizer
    ) {
        RecheckBuilder builder = new RecheckBuilder(candidateId);
        builder.status = "FABRICATED";
        builder.reason = "장애 대응 경험이 있습니다라는 문장은 답변 내부의 명시적 사실과 직접 충돌합니다.";
        builder.improvement = "장애 대응 경험은 아직 부족하지만 관련 상황을 확인하며 대응 역량을 키우고 있습니다.";
        builder.fabricationConfidence = 4;
        builder.directContradiction = true;
        customizer.accept(builder);
        return builder.build();
    }

    private JobPosting mockJobPosting() {
        JobPosting jobPosting = mock(JobPosting.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        Company company = mock(Company.class);
        when(company.getName()).thenReturn("잡드리");
        when(jobPosting.getCompany()).thenReturn(company);
        when(jobPosting.getDetailClassification().getDetailName()).thenReturn("백엔드 개발");
        when(jobPosting.getTask()).thenReturn("API 개발");
        when(jobPosting.getRequirement()).thenReturn("Spring Boot 경험");
        when(jobPosting.getPreferred()).thenReturn("대용량 트래픽 경험");
        return jobPosting;
    }

    private Question mockQuestion() {
        Question question = mock(Question.class);
        when(question.getId()).thenReturn(1L);
        when(question.getContent()).thenReturn("직무 경험을 작성해주세요.");
        when(question.getAnswer()).thenReturn("Spring Boot API를 개발했습니다.");
        return question;
    }

    private AnalysisPromptInput promptInput() {
        return new AnalysisPromptInput(
                "잡드리",
                "백엔드 개발",
                "API 개발",
                "Spring Boot 경험 및 장애 대응 경험",
                "대용량 트래픽 경험",
                List.of(new AnalysisPromptInput.QuestionAnswer(
                        1L,
                        "직무 경험을 작성해주세요.",
                        "Spring Boot API를 개발했습니다. 장애 대응 경험이 있습니다."
                ))
        );
    }

    private AnalysisCandidateResponse.AnalysisCandidate candidate(String candidateId, String sentence) {
        return new AnalysisCandidateResponse.AnalysisCandidate(
                candidateId,
                1L,
                sentence,
                "",
                "",
                "EXPERIENCE",
                "MAIN_TASK",
                "API 개발",
                "MENTIONED",
                "LACK_OF_RESULT",
                "결과가 부족합니다."
        );
    }

    private static class RecheckBuilder {
        private final String candidateId;
        private RecheckDecision decision = RecheckDecision.KEEP_BEST_CANDIDATE;
        private String status = "MENTIONED";
        private String reason = "장애 대응 경험은 언급했지만 구체적인 역할과 결과가 부족합니다.";
        private String improvement = "장애 대응 경험에서 제가 맡은 역할을 정리하고 문제 확인 과정과 처리 결과를 기록하며 대응 역량을 키웠습니다.";
        private Integer problemClarity = 4;
        private Integer jobRelevance = 4;
        private Integer evidenceGap = 4;
        private Integer improvementUsefulness = 4;
        private Integer fabricationConfidence = 1;
        private Boolean questionTypeMatched = true;
        private Boolean contextConsistent = true;
        private Boolean reasonSpecific = true;
        private Boolean improvementActionable = true;
        private Boolean directContradiction = false;

        private RecheckBuilder(String candidateId) {
            this.candidateId = candidateId;
        }

        private CandidateRecheckResponse build() {
            return new CandidateRecheckResponse(
                    decision,
                    candidateId,
                    status,
                    reason,
                    improvement,
                    problemClarity,
                    jobRelevance,
                    evidenceGap,
                    improvementUsefulness,
                    fabricationConfidence,
                    questionTypeMatched,
                    contextConsistent,
                    reasonSpecific,
                    improvementActionable,
                    directContradiction
            );
        }
    }
}
