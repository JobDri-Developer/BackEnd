package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationLlmSnapshot;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeyword;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationQuestionAnalysis;

import java.util.List;
import java.util.Optional;

public final class EvaluationLlmSnapshotMapper {
    private EvaluationLlmSnapshotMapper() {
    }

    public static EvaluationLlmSnapshot from(AnalysisLlmResponse response) {
        if (response == null) {
            return new EvaluationLlmSnapshot(List.of(), List.of(), List.of());
        }
        List<String> keyStrengthQuotes = response.keyStrengths() == null
                ? List.of()
                : response.keyStrengths().stream()
                .map(AnalysisLlmResponse.HighlightItem::quote)
                .toList();
        List<EvaluationMissingKeyword> missingKeywords = response.missingKeywords() == null
                ? List.of()
                : response.missingKeywords().stream()
                .map(EvaluationMissingKeywordMapper::from)
                .flatMap(Optional::stream)
                .toList();
        List<EvaluationQuestionAnalysis> questionAnalyses = response.questionAnalyses() == null
                ? List.of()
                : response.questionAnalyses().stream()
                .map(EvaluationQuestionAnalysisMapper::from)
                .filter(questionAnalysis -> questionAnalysis != null)
                .toList();
        return new EvaluationLlmSnapshot(keyStrengthQuotes, missingKeywords, questionAnalyses);
    }
}
