package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import java.util.Arrays;

final class JobPostingLineBreakFormatter {

    private JobPostingLineBreakFormatter() {
    }

    static String appendLineBreakAfterLastLine(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String formatted = Arrays.stream(normalized.split("\n"))
                .flatMap(line -> Arrays.stream(splitInlineItems(line)))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (formatted.isEmpty()) {
            return formatted;
        }
        return formatted + "\n";
    }

    private static String[] splitInlineItems(String line) {
        String lineSeparatedByMarkers = line.replaceAll(
                "\\s+(?=(?:[-*•·ㆍ]\\s+|\\d+[.)]\\s+))",
                "\n"
        );
        String lineSeparatedBySentences = lineSeparatedByMarkers
                .replaceAll("(?<=[!?。！？])\\s+(?=\\S)", "\n")
                .replaceAll("(?<=[^0-9]\\.)\\s+(?=\\S)", "\n");
        return lineSeparatedBySentences.split("\\n");
    }
}
