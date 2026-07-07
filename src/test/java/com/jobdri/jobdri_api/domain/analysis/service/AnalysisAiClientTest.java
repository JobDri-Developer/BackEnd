package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.config.LlmConcurrencyLimiter;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisAiClientTest {

    private final AnalysisAiClient analysisAiClient = new AnalysisAiClient(
            mock(OpenAIClient.class),
            mock(CorpusRetrievalService.class),
            mock(LlmConcurrencyLimiter.class)
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
}
