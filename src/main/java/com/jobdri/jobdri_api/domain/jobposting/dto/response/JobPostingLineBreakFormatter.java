package com.jobdri.jobdri_api.domain.jobposting.dto.response;

final class JobPostingLineBreakFormatter {

    private JobPostingLineBreakFormatter() {
    }

    static String appendLineBreakAfterLastLine(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            return normalized;
        }
        return normalized + "\n";
    }
}
