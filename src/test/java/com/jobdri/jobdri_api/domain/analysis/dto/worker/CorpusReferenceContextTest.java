package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedJobPostingReference;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedQuestionReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusReferenceContextTest {

    @Test
    @DisplayName("기존 Corpus retrieval 결과를 score 없는 worker context로 변환한다")
    void convertsExistingRetrievalContext() {
        RetrievalContext retrievalContext = new RetrievalContext(
                List.of(new RetrievedJobPostingReference(
                        11L, "회사", "백엔드", "API 개발", "Spring Boot", "AWS", 0.12
                )),
                List.of(new RetrievedQuestionReference(
                        21L, "회사", "백엔드", "EXPERIENCE", 500, "성과 경험을 작성하세요.", 0.21
                ))
        );

        List<CorpusReferenceContext> references = CorpusReferenceContext.from(retrievalContext);

        assertThat(references).hasSize(2);
        assertThat(references.get(0))
                .extracting(
                        CorpusReferenceContext::corpusId,
                        CorpusReferenceContext::category,
                        CorpusReferenceContext::rank
                )
                .containsExactly(11L, "JOB_POSTING", 1);
        assertThat(references.get(0).content()).contains("API 개발", "Spring Boot", "AWS");
        assertThat(references.get(1).category()).isEqualTo("QUESTION");
        assertThat(references.get(1).content()).contains("성과 경험을 작성하세요.", "500");
    }

    @Test
    @DisplayName("Corpus retrieval 결과가 없으면 빈 worker context를 반환한다")
    void returnsEmptyForMissingRetrievalContext() {
        assertThat(CorpusReferenceContext.from(null)).isEmpty();
        assertThat(CorpusReferenceContext.from(new RetrievalContext(List.of(), List.of()))).isEmpty();
    }
}
