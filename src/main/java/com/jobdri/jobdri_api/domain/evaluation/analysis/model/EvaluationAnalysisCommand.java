package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public record EvaluationAnalysisCommand(
        String caseId,
        String jobCategoryMiddle,
        String jobCategorySmall,
        String mainTasks,
        String qualifications,
        String preferences,
        String question,
        String answer,
        java.util.function.Consumer<String> fewShotMetadataRecorder
) {
    public EvaluationAnalysisCommand {
        fewShotMetadataRecorder = fewShotMetadataRecorder == null ? ignored -> {} : fewShotMetadataRecorder;
    }

    public EvaluationAnalysisCommand(String caseId, String jobCategoryMiddle, String jobCategorySmall,
                                     String mainTasks, String qualifications, String preferences,
                                     String question, String answer) {
        this(caseId, jobCategoryMiddle, jobCategorySmall, mainTasks, qualifications, preferences, question, answer, ignored -> {});
    }
}
