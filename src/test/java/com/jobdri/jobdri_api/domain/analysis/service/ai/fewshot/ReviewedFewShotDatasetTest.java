package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.service.ai.FewShotPromptProvider;
import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReviewedFewShotDatasetTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final FewShotProperties properties = reviewedProperties();

    @Test
    void loadsFiveApprovedCasesWithValidPartialAnalysisAndUniqueInputs() throws Exception {
        var cases = store().loadActiveCases();
        assertThat(cases).extracting(FewShotCase::id).containsExactly("FS-02", "FS-03", "FS-05", "FS-08", "FS-09");
        var hashes = new HashSet<String>();
        for (var candidate : cases) {
            assertThat(candidate.reviewStatus()).isEqualTo(FewShotReviewStatus.APPROVED);
            assertThat(candidate.source()).isEqualTo(FewShotSource.REVIEWED_EVALUATION);
            assertThat(candidate.enabled()).isTrue();
            assertThat(candidate.datasetVersion()).isEqualTo(properties.getDatasetVersion());
            assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(candidate.approvedAnalysisJson()))).isTrue();
            assertThat(candidate.promptBlock()).contains(candidate.sanitizedAnswer());
            String marker = "출력 중 강점/문장/누락 관련 필드:";
            assertThat(candidate.promptBlock()).contains(marker);
            assertThat(mapper.readTree(candidate.promptBlock().substring(candidate.promptBlock().indexOf(marker) + marker.length())))
                    .isEqualTo(mapper.readTree(candidate.approvedAnalysisJson()));
            for (var sentence : mapper.readTree(candidate.approvedAnalysisJson()).path("questionAnalyses")) {
                assertThat(candidate.sanitizedAnswer()).contains(sentence.path("sentence").textValue());
            }
            assertThat(hashes.add(FewShotInputHash.of(candidate.mainTasks(), candidate.qualifications(),
                    candidate.question(), candidate.sanitizedAnswer()))).isTrue();
        }
        assertThat(new FewShotProperties().isDynamicSelectionEnabled()).isFalse();
    }

    @Test
    void excludesActualReviewedInputEvenWithDifferentIdAndWhitespace() {
        properties.setDynamicSelectionEnabled(true);
        var store = store();
        var candidate = store.loadActiveCases().getFirst();
        var service = new DefaultFewShotSearchService(store, new FewShotSearchTextBuilder(),
                mock(CohereEmbeddingClient.class), properties, mock(FewShotMetricsRecorder.class));
        var query = new FewShotSearchQuery("HOLDOUT-COPY", candidate.jobCategory(), candidate.jobTitle(),
                candidate.mainTasks(), candidate.qualifications(), candidate.question(),
                "  " + candidate.sanitizedAnswer().replace("\n", "  ") + " ");
        assertThat(service.searchRelevantFewShots(query, 5))
                .isNotEmpty()
                .noneMatch(selected -> selected.fewShotCase().id().equals(candidate.id()));
    }

    @Test
    void invalidJsonResourceRowsDoNotReserveIdsOrBypassSourceValidation() {
        properties.setReviewedEvaluationResource("analysis/fewshot/reviewed-validation-fixture.json");
        var cases = store().loadActiveCases();
        assertThat(cases).extracting(FewShotCase::id).containsExactly("VALID");
        assertThat(cases.getFirst().sanitizedAnswer()).isEqualTo("정상 답변");
    }

    private FewShotCaseStore store() {
        return new FewShotCaseStore(new FewShotPromptProvider(), properties, mapper);
    }

    private static FewShotProperties reviewedProperties() {
        var properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setDatasetVersion("fewshot-pm-reviewed-20260914-v2");
        properties.setReviewedEvaluationResource("analysis/fewshot/reviewed-fewshot-cases-pm-20260914-v2.json");
        return properties;
    }
}
