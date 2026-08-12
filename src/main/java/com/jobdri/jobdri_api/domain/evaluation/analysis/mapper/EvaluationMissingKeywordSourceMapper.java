package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSource;

import java.util.Optional;

public final class EvaluationMissingKeywordSourceMapper {
    private EvaluationMissingKeywordSourceMapper() {
    }

    public static Optional<EvaluationMissingKeywordSource> fromAnalysisSource(String value) {
        return EvaluationMissingKeywordSource.from(value);
    }

    public static Optional<EvaluationMissingKeywordSource> fromAnalysisSource(MissingKeywordSource source) {
        if (source == null) {
            return Optional.empty();
        }
        return switch (source) {
            case MAIN_TASK -> Optional.of(EvaluationMissingKeywordSource.MAIN_TASK);
            case QUALIFICATION -> Optional.of(EvaluationMissingKeywordSource.QUALIFICATION);
            case PREFERENCE -> Optional.of(EvaluationMissingKeywordSource.PREFERENCE);
        };
    }

    public static MissingKeywordSource toAnalysisSource(EvaluationMissingKeywordSource source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case MAIN_TASK -> MissingKeywordSource.MAIN_TASK;
            case QUALIFICATION -> MissingKeywordSource.QUALIFICATION;
            case PREFERENCE -> MissingKeywordSource.PREFERENCE;
        };
    }
}
