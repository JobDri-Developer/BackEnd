package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateSnapshot;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordCandidate;

import java.util.List;

public final class EvaluationCandidateSnapshotMapper {
    private EvaluationCandidateSnapshotMapper() {
    }

    public static EvaluationCandidateSnapshot from(AnalysisCandidateResponse response) {
        if (response == null || response.missingKeywordCandidates() == null) {
            return new EvaluationCandidateSnapshot(List.of());
        }
        List<EvaluationMissingKeywordCandidate> missingKeywordCandidates = response.missingKeywordCandidates().stream()
                .map(candidate -> EvaluationMissingKeywordSourceMapper.fromAnalysisSource(candidate.source())
                        .map(source -> new EvaluationMissingKeywordCandidate(
                                candidate.keyword(),
                                source,
                                candidate.relatedRequirement()
                        ))
                        .orElse(null))
                .filter(candidate -> candidate != null)
                .toList();
        return new EvaluationCandidateSnapshot(missingKeywordCandidates);
    }
}
