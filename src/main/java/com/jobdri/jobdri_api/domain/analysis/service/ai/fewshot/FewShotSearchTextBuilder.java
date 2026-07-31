package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class FewShotSearchTextBuilder {
    public String buildQueryText(FewShotSearchQuery query) {
        return """
                [JOB_CATEGORY]
                %s

                [JOB_TITLE]
                %s

                [MAIN_TASKS]
                %s

                [QUALIFICATIONS]
                %s

                [QUESTION]
                %s

                [ANSWER]
                %s
                """.formatted(
                value(query.jobCategory()),
                value(query.jobTitle()),
                lines(query.mainTasks()),
                lines(query.qualifications()),
                value(query.question()),
                value(query.answer())
        );
    }

    public String buildCandidateDocument(FewShotCase fewShotCase) {
        return """
                [CASE_ID]
                %s

                [SOURCE]
                %s

                [JOB_CATEGORY]
                %s

                [JOB_TITLE]
                %s

                [JOB_REQUIREMENTS]
                %s
                %s

                [QUESTION]
                %s

                [ANSWER]
                %s

                [TAGS]
                %s
                """.formatted(
                value(fewShotCase.id()),
                fewShotCase.source(),
                value(fewShotCase.jobCategory()),
                value(fewShotCase.jobTitle()),
                lines(fewShotCase.mainTasks()),
                lines(fewShotCase.qualifications()),
                value(fewShotCase.question()),
                value(fewShotCase.sanitizedAnswer()),
                String.join(", ", fewShotCase.tags())
        );
    }

    private static String lines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(line -> line.startsWith("-") ? line : "- " + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
