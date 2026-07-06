package com.jobdri.jobdri_api.domain.analysis.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

public enum MissingKeywordSource {
    QUALIFICATION("qualification"),
    PREFERENCE("preference"),
    MAIN_TASK("mainTask");

    private final String value;

    MissingKeywordSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MissingKeywordSource fromJson(String value) {
        return from(value).orElse(null);
    }

    public static Optional<MissingKeywordSource> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(source -> source.value.equals(value.trim()))
                .findFirst();
    }
}
