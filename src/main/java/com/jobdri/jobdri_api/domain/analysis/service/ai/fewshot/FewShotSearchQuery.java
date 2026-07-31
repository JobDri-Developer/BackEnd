package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptInput;

import java.util.List;

public record FewShotSearchQuery(
        String caseId,
        String jobCategory,
        String jobTitle,
        List<String> mainTasks,
        List<String> qualifications,
        String question,
        String answer
) {
    public FewShotSearchQuery {
        mainTasks = mainTasks == null ? List.of() : List.copyOf(mainTasks);
        qualifications = qualifications == null ? List.of() : List.copyOf(qualifications);
    }

    public static FewShotSearchQuery from(AnalysisPromptInput input) {
        String question = "";
        String answer = "";
        if (input.questions() != null && !input.questions().isEmpty()) {
            question = input.questions().stream()
                    .map(AnalysisPromptInput.QuestionAnswer::question)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            answer = input.questions().stream()
                    .map(AnalysisPromptInput.QuestionAnswer::answer)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
        return new FewShotSearchQuery(
                input.caseId(),
                input.jobName(),
                input.jobName(),
                splitLines(input.mainTasks()),
                splitLines(input.qualifications()),
                question,
                answer
        );
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
