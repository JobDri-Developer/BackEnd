package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class FewShotSearchTextBuilder {
    static final int MAX_IDENTIFIER_LENGTH = 100;
    static final int MAX_SHORT_FIELD_LENGTH = 200;
    static final int MAX_REQUIREMENTS_LENGTH = 1_200;
    static final int MAX_QUESTION_LENGTH = 600;
    static final int MAX_ANSWER_LENGTH = 2_000;
    static final int MAX_TAGS_LENGTH = 500;

    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>"
    );
    private static final Pattern BLOCK_HTML_TAG_PATTERN = Pattern.compile(
            "(?is)</?(?:br|p|div|li|ul|ol|h[1-6]|tr|td|th|section|article)\\b[^>]*>"
    );
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)</?[a-z][a-z0-9:-]*\\b[^>]*>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\p{Z}\\s]+");

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
                value(query.jobCategory(), MAX_SHORT_FIELD_LENGTH),
                value(query.jobTitle(), MAX_SHORT_FIELD_LENGTH),
                lines(query.mainTasks(), MAX_REQUIREMENTS_LENGTH),
                lines(query.qualifications(), MAX_REQUIREMENTS_LENGTH),
                value(query.question(), MAX_QUESTION_LENGTH),
                value(query.answer(), MAX_ANSWER_LENGTH)
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
                value(fewShotCase.id(), MAX_IDENTIFIER_LENGTH),
                fewShotCase.source(),
                value(fewShotCase.jobCategory(), MAX_SHORT_FIELD_LENGTH),
                value(fewShotCase.jobTitle(), MAX_SHORT_FIELD_LENGTH),
                lines(fewShotCase.mainTasks(), MAX_REQUIREMENTS_LENGTH),
                lines(fewShotCase.qualifications(), MAX_REQUIREMENTS_LENGTH),
                value(fewShotCase.question(), MAX_QUESTION_LENGTH),
                value(fewShotCase.sanitizedAnswer(), MAX_ANSWER_LENGTH),
                values(fewShotCase.tags(), MAX_TAGS_LENGTH)
        );
    }

    private static String lines(List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .map(FewShotSearchTextBuilder::normalize)
                .filter(StringUtils::hasText)
                .map(line -> line.startsWith("-") ? line : "- " + line)
                .collect(Collectors.joining("\n"));
        return truncate(joined, maxLength);
    }

    private static String values(List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .map(FewShotSearchTextBuilder::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));
        return truncate(joined, maxLength);
    }

    private static String value(String value, int maxLength) {
        return truncate(normalize(value), maxLength);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String decoded = HtmlUtils.htmlUnescape(value);
        String withoutExecutableContent = SCRIPT_STYLE_PATTERN.matcher(decoded).replaceAll(" ");
        String withoutComments = HTML_COMMENT_PATTERN.matcher(withoutExecutableContent).replaceAll(" ");
        String withBlockSeparators = BLOCK_HTML_TAG_PATTERN.matcher(withoutComments).replaceAll(" ");
        String withoutTags = HTML_TAG_PATTERN.matcher(withBlockSeparators).replaceAll("");
        return WHITESPACE_PATTERN.matcher(withoutTags).replaceAll(" ").trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
