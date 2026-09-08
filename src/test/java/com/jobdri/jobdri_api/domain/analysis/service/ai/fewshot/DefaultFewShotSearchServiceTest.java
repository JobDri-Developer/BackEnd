package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultFewShotSearchServiceTest {
    private final FewShotCaseStore caseStore = mock(FewShotCaseStore.class);
    private final FewShotSearchTextBuilder textBuilder = new FewShotSearchTextBuilder();
    private final CohereEmbeddingClient cohereEmbeddingClient = mock(CohereEmbeddingClient.class);
    private final FewShotProperties properties = new FewShotProperties();
    private final DefaultFewShotSearchService service = new DefaultFewShotSearchService(
            caseStore,
            textBuilder,
            cohereEmbeddingClient,
            properties
    );

    @Test
    @DisplayName("feature flag가 꺼져 있으면 후보 검색을 수행하지 않는다")
    void disabledDynamicSelectionReturnsEmpty() {
        properties.setDynamicSelectionEnabled(false);

        List<SelectedFewShotCase> result = service.searchRelevantFewShots(query("EV-01"), 3);

        assertThat(result).isEmpty();
        verify(caseStore, never()).loadActiveCases();
    }

    @Test
    @DisplayName("평가 caseId와 같은 후보는 자기 참조 방지를 위해 제외한다")
    void excludesSelfReferenceCandidate() {
        properties.setDynamicSelectionEnabled(true);
        when(caseStore.loadActiveCases()).thenReturn(List.of(
                caseItem("EV-01", "Spring Boot 경험", 10),
                caseItem("EV-02", "Spring Boot API 개발", 5)
        ));
        when(cohereEmbeddingClient.embedQuery(any())).thenReturn(new float[]{1, 0});
        when(cohereEmbeddingClient.embedDocuments(any())).thenReturn(List.of(new float[]{1, 0}));

        List<SelectedFewShotCase> result = service.searchRelevantFewShots(query("EV-01"), 3);

        assertThat(result).extracting(item -> item.fewShotCase().id())
                .containsExactly("EV-02");
    }

    @Test
    @DisplayName("caseId가 달라도 정규화된 JD, 문항, 답변이 같으면 후보에서 제외한다")
    void excludesCandidateWithSameNormalizedInput() {
        properties.setDynamicSelectionEnabled(true);
        when(caseStore.loadActiveCases()).thenReturn(List.of(
                caseItem("FS-SAME", "  spring BOOT API를  개발했습니다. ", 10),
                caseItem("FS-OTHER", "Spring Boot API를 운영하고 장애를 개선했습니다.", 5)
        ));
        when(cohereEmbeddingClient.embedQuery(any())).thenReturn(new float[]{1, 0});
        when(cohereEmbeddingClient.embedDocuments(any())).thenReturn(List.of(new float[]{1, 0}));

        List<SelectedFewShotCase> result = service.searchRelevantFewShots(query("EV-01"), 3);

        assertThat(result).extracting(item -> item.fewShotCase().id())
                .containsExactly("FS-OTHER");
    }

    @Test
    @DisplayName("Cohere 선택 실패 시 로컬 선택으로 fallback한다")
    void fallsBackToLocalSelectionWhenCohereFails() {
        properties.setDynamicSelectionEnabled(true);
        when(caseStore.loadActiveCases()).thenReturn(List.of(
                caseItem("FS-1", "Spring Boot API 개발", 0),
                caseItem("FS-2", "브랜드 운영", 0)
        ));
        when(cohereEmbeddingClient.embedQuery(any())).thenThrow(new RuntimeException("cohere down"));

        List<SelectedFewShotCase> result = service.searchRelevantFewShots(query("EV-99"), 1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().selectionMethod()).isEqualTo("local-fallback");
    }

    @Test
    @DisplayName("서로 다른 검색 요청에서도 동일한 후보의 document embedding을 재사용한다")
    void reusesDocumentEmbeddingAcrossDifferentQueries() {
        properties.setDynamicSelectionEnabled(true);
        when(caseStore.loadActiveCases()).thenReturn(List.of(
                caseItem("FS-1", "Spring Boot API 개발", 0),
                caseItem("FS-2", "브랜드 운영", 0)
        ));
        when(cohereEmbeddingClient.embedQuery(any())).thenReturn(new float[]{1, 0});
        when(cohereEmbeddingClient.embedDocuments(any())).thenReturn(List.of(
                new float[]{1, 0},
                new float[]{0, 1}
        ));

        service.searchRelevantFewShots(query("EV-01", "Spring Boot API를 개발했습니다."), 1);
        service.searchRelevantFewShots(query("EV-02", "Java 서버를 운영했습니다."), 1);

        verify(cohereEmbeddingClient, times(2)).embedQuery(any());
        verify(cohereEmbeddingClient, times(1)).embedDocuments(any());
    }

    @Test
    @DisplayName("후보 내용이 변경되면 document embedding을 다시 생성한다")
    void refreshesDocumentEmbeddingWhenCandidateContentChanges() {
        properties.setDynamicSelectionEnabled(true);
        when(caseStore.loadActiveCases()).thenReturn(
                List.of(caseItem("FS-1", "Spring Boot API 개발", 0)),
                List.of(caseItem("FS-1", "브랜드 운영 경험", 0))
        );
        when(cohereEmbeddingClient.embedQuery(any())).thenReturn(new float[]{1, 0});
        when(cohereEmbeddingClient.embedDocuments(any())).thenReturn(List.of(new float[]{1, 0}));

        service.searchRelevantFewShots(query("EV-01", "Spring Boot API를 개발했습니다."), 1);
        service.searchRelevantFewShots(query("EV-01", "Spring Boot API를 개발했습니다."), 1);

        verify(cohereEmbeddingClient, times(2)).embedQuery(any());
        verify(cohereEmbeddingClient, times(2)).embedDocuments(any());
    }

    @Test
    @DisplayName("캐시가 비활성화되면 매 요청마다 document embedding을 생성한다")
    void embedsDocumentsOnEveryRequestWhenCacheDisabled() {
        properties.setDynamicSelectionEnabled(true);
        properties.setCacheEnabled(false);
        when(caseStore.loadActiveCases()).thenReturn(List.of(caseItem("FS-1", "Spring Boot API 개발", 0)));
        when(cohereEmbeddingClient.embedQuery(any())).thenReturn(new float[]{1, 0});
        when(cohereEmbeddingClient.embedDocuments(any())).thenReturn(List.of(new float[]{1, 0}));

        service.searchRelevantFewShots(query("EV-01", "Spring Boot API를 개발했습니다."), 1);
        service.searchRelevantFewShots(query("EV-02", "Java 서버를 운영했습니다."), 1);

        verify(cohereEmbeddingClient, times(2)).embedDocuments(any());
    }

    private static FewShotSearchQuery query(String caseId) {
        return query(caseId, "Spring Boot API를 개발했습니다.");
    }

    private static FewShotSearchQuery query(String caseId, String answer) {
        return new FewShotSearchQuery(
                caseId,
                "백엔드 개발",
                "Backend Engineer",
                List.of("Spring Boot API 개발"),
                List.of("Java"),
                "지원 직무 경험",
                answer
        );
    }

    private static FewShotCase caseItem(String id, String answer, int priority) {
        return new FewShotCase(
                id,
                FewShotSource.REVIEWED_EVALUATION,
                FewShotReviewStatus.APPROVED,
                true,
                priority,
                "백엔드 개발",
                "Backend Engineer",
                List.of("Spring Boot API 개발"),
                List.of("Java"),
                "지원 직무 경험",
                answer,
                "{\"questionAnalyses\":[]}",
                List.of("spring"),
                "fewshot-test-v1",
                "## 예시 " + id
        );
    }
}
