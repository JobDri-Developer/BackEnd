package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisAiClientTest {

    private final AnalysisAiClient analysisAiClient = new AnalysisAiClient(
            mock(AsyncMetricsRecorder.class),
            mock(OpenAIClient.class),
            mock(CorpusRetrievalService.class),
            mock(LlmConcurrencyLimiter.class),
            new FewShotPromptProvider(),
            new ObjectMapper()
    );

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
                                        99L,
                                        "Spring Boot API를 개발했습니다.",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "잘못된 questionId입니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        1L,
                                        "답변에 없는 문장",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "원문에 없습니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
                                        "EXPERIENCE",
                                        "PREFERENCE",
                                        "대용량 트래픽",
                                        "MENTIONED",
                                        "LACK_OF_RESULT",
                                        "preference-only입니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
                                        "EXPERIENCE",
                                        "MAIN_TASK",
                                        "API 개발",
                                        "PROVEN",
                                        "LACK_OF_RESULT",
                                        "허용하지 않은 status입니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        1L,
                                        "장애 대응 경험이 있습니다.",
                                        "EXPERIENCE",
                                        "QUALIFICATION",
                                        "장애 대응 경험",
                                        "FABRICATED",
                                        "DIRECT_CONTRADICTION",
                                        ""
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        1L,
                                        "장애 대응 경험이 있습니다.",
                                        "EXPERIENCE",
                                        "QUALIFICATION",
                                        "장애 대응 경험",
                                        "FABRICATED",
                                        "DIRECT_CONTRADICTION",
                                        "답변 내부의 명시적 사실과 직접 충돌합니다."
                                ),
                                new AnalysisCandidateResponse.AnalysisCandidate(
                                        1L,
                                        "Spring Boot API를 개발했습니다.",
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
                        1L,
                        "장애 대응 경험이 있습니다.",
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
}
