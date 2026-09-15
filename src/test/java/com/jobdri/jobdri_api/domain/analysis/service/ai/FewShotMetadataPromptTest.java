package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.*;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class FewShotMetadataPromptTest {
    private final FewShotSearchService search = mock(FewShotSearchService.class);
    private final FewShotProperties properties = new FewShotProperties();
    private final FewShotPromptProvider provider = new FewShotPromptProvider();
    private final AnalysisPromptBuilder builder = new AnalysisPromptBuilder(provider, search, properties, new ObjectMapper());
    private final List<FewShotSelectionMetadata> recorded = new ArrayList<>();

    private String build() {
        return builder.buildSinglePassPrompt(
                new AnalysisPromptInput("EV", "회사", "개발", "API", "Java", "",
                        List.of(new AnalysisPromptInput.QuestionAnswer(1L, "질문", "비밀 원문"))),
                new RetrievalContext(List.of(), List.of()), null, recorded::add);
    }

    @Test
    void staticReportsActualFixedIdsAndNoSimilarity() {
        assertThat(build()).contains(provider.getPrompt());
        var meta = recorded.getFirst();
        assertThat(meta.selectionMode()).isEqualTo("STATIC");
        assertThat(meta.selectedCases()).hasSize(provider.getFixedExampleBlocks().size());
        assertThat(meta.selectedCases().getFirst().id()).isEqualTo("FS-FIXED-1");
        assertThat(meta.topScore()).isNull();
        verifyNoInteractions(search);
    }

    @Test
    void embeddingSnapshotMatchesPromptAndIsRecordedOnce() throws Exception {
        properties.setDynamicSelectionEnabled(true);
        properties.setDatasetVersion("version-2");
        properties.getSearch().setTopK(2);
        properties.getSearch().setMinSimilarity(0.3);
        when(search.searchRelevantFewShots(any(), anyInt())).thenReturn(List.of(
                selected("A", 0.4, "cohere-embedding"), selected("B", 0.8, "cohere-embedding")));
        assertThat(build()).contains("BLOCK-A", "BLOCK-B").doesNotContain(provider.getPrompt());
        assertThat(recorded).hasSize(1);
        var meta = recorded.getFirst();
        assertThat(meta.selectionMode()).isEqualTo("EMBEDDING");
        assertThat(meta.topScore()).isEqualTo(0.8);
        assertThat(meta.bottomScore()).isEqualTo(0.4);
        assertThat(meta.avgScore()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(meta.datasetVersion()).isEqualTo("version-2");
        assertThat(meta.minSimilarity()).isEqualTo(0.3);
        assertThat(meta.topK()).isEqualTo(2);
        assertThat(meta.selectedCases()).extracting(FewShotSelectionMetadata.Candidate::id).containsExactly("A", "B");
        assertThat(new ObjectMapper().writeValueAsString(meta)).doesNotContain("비밀 원문", "BLOCK-", "sanitizedAnswer", "promptBlock");
        verify(search, times(1)).searchRelevantFewShots(any(), anyInt());
    }

    @Test
    void localScoreIsNotLabeledAsCosineSimilarity() {
        properties.setDynamicSelectionEnabled(true);
        when(search.searchRelevantFewShots(any(), anyInt())).thenReturn(List.of(selected("L", 1.2, "local-fallback")));
        build();
        assertThat(recorded.getFirst().selectionMode()).isEqualTo("LOCAL_FALLBACK");
        assertThat(recorded.getFirst().scoreType()).isEqualTo("LOCAL_HEURISTIC");
    }

    @Test
    void emptyAndExceptionFallbackRecordStaticExamplesNotEmptyCandidates() {
        properties.setDynamicSelectionEnabled(true);
        when(search.searchRelevantFewShots(any(), anyInt())).thenReturn(List.of()).thenThrow(new RuntimeException("failure"));
        assertThat(build()).contains(provider.getPrompt());
        assertThat(build()).contains(provider.getPrompt());
        assertThat(recorded).allSatisfy(meta -> {
            assertThat(meta.selectionMode()).isEqualTo("STATIC_FALLBACK");
            assertThat(meta.selectedCases()).hasSize(provider.getFixedExampleBlocks().size());
            assertThat(meta.avgScore()).isNull();
        });
        assertThat(recorded.get(0).reason()).isEqualTo("empty_selection");
        assertThat(recorded.get(1).reason()).isEqualTo("selection_exception");
    }

    @Test
    void twoPassIsNotReportedAsStatic() {
        assertThat(builder.fewShotNotApplied().selectionMode()).isEqualTo("NOT_APPLIED");
        assertThat(builder.fewShotNotApplied().selectedCases()).isEmpty();
    }

    private SelectedFewShotCase selected(String id, double score, String method) {
        return new SelectedFewShotCase(new FewShotCase(id, FewShotSource.REVIEWED_EVALUATION,
                FewShotReviewStatus.APPROVED, true, 1, "", "", List.of(), List.of(), "질문",
                "비밀 원문", "{}", List.of(), "version-2", "BLOCK-" + id), score, method);
    }
}
