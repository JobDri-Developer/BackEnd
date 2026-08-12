package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

import java.util.Arrays;
import java.util.Optional;

public enum EvaluationMissingKeywordSource {
    MAIN_TASK("mainTask", "MAIN_TASK", "MAIN_TASKS"),
    QUALIFICATION("qualification", "QUALIFICATION", "QUALIFICATIONS"),
    PREFERENCE("preference", "PREFERENCE", "PREFERENCES");

    private final String value;
    private final String[] aliases;

    EvaluationMissingKeywordSource(String value, String... aliases) {
        this.value = value;
        this.aliases = aliases;
    }

    public String value() {
        return value;
    }

    public static Optional<EvaluationMissingKeywordSource> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(source -> source.value.equalsIgnoreCase(normalized)
                        || Arrays.stream(source.aliases).anyMatch(alias -> alias.equalsIgnoreCase(normalized)))
                .findFirst();
    }
}
