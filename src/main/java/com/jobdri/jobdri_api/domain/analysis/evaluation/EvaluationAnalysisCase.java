package com.jobdri.jobdri_api.domain.analysis.evaluation;

record EvaluationAnalysisCase(
        String caseId,
        String jobCategoryMiddle,
        String jobCategorySmall,
        String mainTasks,
        String qualifications,
        String preferences,
        String question,
        String answer
) {
}
