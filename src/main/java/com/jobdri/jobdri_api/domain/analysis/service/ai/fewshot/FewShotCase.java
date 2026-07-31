package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import java.util.List;

public record FewShotCase(
        String id,
        FewShotSource source,
        FewShotReviewStatus reviewStatus,
        boolean enabled,
        int priority,
        String jobCategory,
        String jobTitle,
        List<String> mainTasks,
        List<String> qualifications,
        String question,
        String sanitizedAnswer,
        String approvedAnalysisJson,
        List<String> tags,
        String datasetVersion,
        String promptBlock
) {
    public FewShotCase {
        mainTasks = mainTasks == null ? List.of() : List.copyOf(mainTasks);
        qualifications = qualifications == null ? List.of() : List.copyOf(qualifications);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean searchable() {
        return enabled
                && reviewStatus == FewShotReviewStatus.APPROVED
                && approvedAnalysisJson != null
                && !approvedAnalysisJson.isBlank();
    }
}
