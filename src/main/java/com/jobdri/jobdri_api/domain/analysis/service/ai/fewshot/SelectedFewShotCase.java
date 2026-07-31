package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

public record SelectedFewShotCase(
        FewShotCase fewShotCase,
        double score,
        String selectionMethod
) {
}
