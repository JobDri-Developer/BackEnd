package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeyword;

import java.util.Optional;

public final class EvaluationMissingKeywordMapper {
    private EvaluationMissingKeywordMapper() {
    }

    public static Optional<EvaluationMissingKeyword> from(AnalysisLlmResponse.MissingKeywordItem item) {
        if (item == null) {
            return Optional.empty();
        }
        return EvaluationMissingKeywordSourceMapper.fromAnalysisSource(item.source())
                .map(source -> new EvaluationMissingKeyword(item.keyword(), source));
    }
}
