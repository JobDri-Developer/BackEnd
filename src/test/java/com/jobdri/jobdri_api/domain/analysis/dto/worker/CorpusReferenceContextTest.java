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
        assertThat(references.get(0)).isEqualTo(new CorpusReferenceContext(
                11L,
                "JOB_POSTING",
                "회사 - 백엔드",
                "주요 업무: API 개발\n자격 요건: Spring Boot\n우대 사항: AWS",
                1
        ));
        assertThat(references.get(1)).isEqualTo(new CorpusReferenceContext(
                21L,
                "QUESTION",
                "회사 - 백엔드 - EXPERIENCE",
                "문항: 성과 경험을 작성하세요.\n글자 수 제한: 500",
                1
        ));
    }

    @Test
    @DisplayName("Corpus retrieval 결과가 없으면 빈 worker context를 반환한다")
    void returnsEmptyForMissingRetrievalContext() {
        assertThat(CorpusReferenceContext.from(null)).isEmpty();
        assertThat(CorpusReferenceContext.from(new RetrievalContext(List.of(), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("blank 필드는 빈 title과 content로 정규화한다")
    void normalizesBlankFields() {
        RetrievalContext retrievalContext = new RetrievalContext(
                List.of(new RetrievedJobPostingReference(11L, " ", null, "", " ", null, 0.1)),
                List.of()
        );

        CorpusReferenceContext reference = CorpusReferenceContext.from(retrievalContext).getFirst();

        assertThat(reference.title()).isEmpty();
        assertThat(reference.content()).isEmpty();
    }

    @Test
    @DisplayName("문항 charLimit이 null이면 글자 수 라벨을 추가하지 않는다")
    void omitsMissingQuestionCharLimit() {
        RetrievalContext retrievalContext = new RetrievalContext(
                List.of(),
                List.of(new RetrievedQuestionReference(
                        21L, "회사", "백엔드", "MOTIVATION", null, "지원 동기를 작성하세요.", 0.2
                ))
        );

        CorpusReferenceContext reference = CorpusReferenceContext.from(retrievalContext).getFirst();

        assertThat(reference.content()).isEqualTo("문항: 지원 동기를 작성하세요.");
    }

    @Test
    @DisplayName("RetrievalContext 내부 null 리스트는 빈 목록으로 처리한다")
    void handlesNullReferenceLists() {
        assertThat(CorpusReferenceContext.from(new RetrievalContext(null, null))).isEmpty();
    }
}
