package com.jobdri.jobdri_api.domain.analysis.dto.criteria;

import java.util.List;

public record JobCategoryEvaluationCriteria(
        String jobCategoryMiddle,
        List<String> includedJobs,
        List<String> competencyTags,
        List<String> coreCompetencies,
        List<String> relatedActions,
        List<String> relatedExperiences,
        List<String> relatedKeywords,
        String goodEvidenceExample,
        List<String> missingKeywordExamples
) {
}
