package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import java.util.List;
import java.util.stream.IntStream;

/** Prompt-selection snapshot only: never contains answers, JD text or embeddings. */
public record FewShotSelectionMetadata(
        String selectionMode,
        String reason,
        String datasetVersion,
        double minSimilarity,
        int topK,
        int minimumSelectedCount,
        String scoreType,
        List<Candidate> selectedCases,
        Double topScore,
        Double bottomScore,
        Double avgScore
) {
    public FewShotSelectionMetadata {
        selectedCases = List.copyOf(selectedCases);
    }

    public record Candidate(String id, String source, Double score, String datasetVersion) {
    }

    public static FewShotSelectionMetadata selected(List<SelectedFewShotCase> selected, FewShotProperties properties) {
        boolean embedding = selected.stream().allMatch(item -> "cohere-embedding".equals(item.selectionMethod()));
        var scores = selected.stream().mapToDouble(SelectedFewShotCase::score).summaryStatistics();
        return new FewShotSelectionMetadata(
                embedding ? "EMBEDDING" : "LOCAL_FALLBACK", "", properties.getDatasetVersion(),
                properties.getSearch().getMinSimilarity(), properties.getSearch().getTopK(),
                properties.getSearch().getMinimumSelectedCount(), embedding ? "COSINE_SIMILARITY" : "LOCAL_HEURISTIC",
                selected.stream().map(item -> new Candidate(item.fewShotCase().id(),
                        item.fewShotCase().source().name(), item.score(), item.fewShotCase().datasetVersion())).toList(),
                scores.getMax(), scores.getMin(), scores.getAverage());
    }

    public static FewShotSelectionMetadata staticSelection(String mode, String reason, int exampleCount,
                                                           FewShotProperties configured) {
        var properties = configured == null ? new FewShotProperties() : configured;
        return new FewShotSelectionMetadata(mode, reason, properties.getDatasetVersion(),
                properties.getSearch().getMinSimilarity(), properties.getSearch().getTopK(),
                properties.getSearch().getMinimumSelectedCount(), "NONE",
                IntStream.rangeClosed(1, exampleCount)
                        .mapToObj(i -> new Candidate("FS-FIXED-" + i, "FIXED", null, "static-resource")).toList(),
                null, null, null);
    }
}
