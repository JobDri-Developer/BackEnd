package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.analysis.service.ai.FewShotPromptProvider;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedJobPostingReference;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedQuestionReference;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.cohere.CohereProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisInputFingerprintProviderTest {

    private final FewShotPromptProvider fewShotPromptProvider = mock(FewShotPromptProvider.class);
    private final AnalysisInputFingerprintProvider provider = new AnalysisInputFingerprintProvider(
            new ObjectMapper(),
            fewShotPromptProvider,
            new CohereProperties(null, null, null),
            "gpt-4o-mini",
            false,
            "",
            3,
            5
    );

    @Test
    @DisplayName("유사 공고 Prompt context가 달라지면 fingerprint가 달라진다")
    void fingerprintChangesWhenSimilarJobPostingContextChanges() {
        when(fewShotPromptProvider.getPrompt()).thenReturn("few-shot");
        JobPosting current = currentJobPosting();
        AnalysisExecutionPayload first = payload(current, similarContext("Spring Boot API 개발", 0.91));
        AnalysisExecutionPayload changed = payload(current, similarContext("Kotlin API 개발", 0.91));

        assertThat(provider.create(first)).isNotEqualTo(provider.create(changed));
    }

    @Test
    @DisplayName("유사도 점수의 미세한 변화는 fingerprint에 영향을 주지 않는다")
    void fingerprintIgnoresSimilarityScore() {
        when(fewShotPromptProvider.getPrompt()).thenReturn("few-shot");
        JobPosting current = currentJobPosting();
        AnalysisExecutionPayload first = payload(current, similarContext("Spring Boot API 개발", 0.910001));
        AnalysisExecutionPayload changedScore = payload(current, similarContext("Spring Boot API 개발", 0.909999));

        assertThat(provider.create(first)).isEqualTo(provider.create(changedScore));
    }

    @Test
    @DisplayName("Curated Corpus 내용이 달라지면 fingerprint가 달라진다")
    void fingerprintChangesWhenCorpusContentChanges() {
        when(fewShotPromptProvider.getPrompt()).thenReturn("few-shot");
        JobPosting current = currentJobPosting();
        AnalysisExecutionPayload first = payload(current, corpusReference("Spring Boot", 0.1));
        AnalysisExecutionPayload changed = payload(current, corpusReference("Kotlin", 0.1));

        assertThat(provider.create(first)).isNotEqualTo(provider.create(changed));
    }

    @Test
    @DisplayName("Curated Corpus distance 변화는 fingerprint에 영향을 주지 않는다")
    void fingerprintIgnoresCorpusDistance() {
        when(fewShotPromptProvider.getPrompt()).thenReturn("few-shot");
        JobPosting current = currentJobPosting();
        AnalysisExecutionPayload first = payload(current, corpusReference("Spring Boot", 0.1));
        AnalysisExecutionPayload changedDistance = payload(current, corpusReference("Spring Boot", 0.9));

        assertThat(provider.create(first)).isEqualTo(provider.create(changedDistance));
    }

    @Test
    @DisplayName("Curated Corpus 문항 내용이 달라지면 fingerprint가 달라진다")
    void fingerprintChangesWhenCorpusQuestionContentChanges() {
        when(fewShotPromptProvider.getPrompt()).thenReturn("few-shot");
        JobPosting current = currentJobPosting();
        AnalysisExecutionPayload first = payload(current, questionCorpusReference("성과 경험", 0.1));
        AnalysisExecutionPayload changed = payload(current, questionCorpusReference("지원 동기", 0.1));

        assertThat(provider.create(first)).isNotEqualTo(provider.create(changed));
    }

    @Test
    @DisplayName("Curated Corpus 문항 distance 변화는 fingerprint에 영향을 주지 않는다")
    void fingerprintIgnoresCorpusQuestionDistance() {
        when(fewShotPromptProvider.getPrompt()).thenReturn("few-shot");
        JobPosting current = currentJobPosting();
        AnalysisExecutionPayload first = payload(current, questionCorpusReference("성과 경험", 0.1));
        AnalysisExecutionPayload changedDistance = payload(current, questionCorpusReference("성과 경험", 0.9));

        assertThat(provider.create(first)).isEqualTo(provider.create(changedDistance));
    }

    private AnalysisExecutionPayload payload(JobPosting jobPosting, SimilarJobPostingContext context) {
        return new AnalysisExecutionPayload(
                1L,
                10L,
                jobPosting,
                List.of(),
                List.of(),
                null,
                null,
                List.of(context)
        );
    }

    private AnalysisExecutionPayload payload(JobPosting jobPosting, RetrievalContext retrievalContext) {
        return new AnalysisExecutionPayload(
                1L,
                10L,
                jobPosting,
                List.of(),
                List.of(),
                null,
                retrievalContext,
                List.of()
        );
    }

    private RetrievalContext corpusReference(String requirements, double distance) {
        return new RetrievalContext(
                List.of(new RetrievedJobPostingReference(
                        11L,
                        "참고 회사",
                        "백엔드 개발자",
                        "API 개발",
                        requirements,
                        "AWS",
                        distance
                )),
                List.of()
        );
    }

    private RetrievalContext questionCorpusReference(String questionText, double distance) {
        return new RetrievalContext(
                List.of(),
                List.of(new RetrievedQuestionReference(
                        21L,
                        "참고 회사",
                        "백엔드 개발자",
                        "EXPERIENCE",
                        500,
                        questionText,
                        distance
                ))
        );
    }

    private SimilarJobPostingContext similarContext(String task, double score) {
        return new SimilarJobPostingContext(
                31L,
                "유사 회사",
                "유사 공고",
                "서버 개발자",
                task,
                "Java",
                "AWS",
                1,
                score
        );
    }

    private JobPosting currentJobPosting() {
        JobPosting posting = mock(JobPosting.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(posting.getCompany().getName()).thenReturn("현재 회사");
        when(posting.getDetailClassification().getMiddleClassification().getClassification().getBigName())
                .thenReturn("개발");
        when(posting.getDetailClassification().getMiddleClassification().getMiddleName()).thenReturn("서버");
        when(posting.getDetailClassification().getDetailName()).thenReturn("백엔드");
        when(posting.getPostingName()).thenReturn("현재 공고");
        when(posting.getJobTitle()).thenReturn("백엔드 개발자");
        when(posting.getTask()).thenReturn("API 개발");
        when(posting.getRequirement()).thenReturn("Java");
        when(posting.getPreferred()).thenReturn("AWS");
        return posting;
    }
}
