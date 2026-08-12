package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationCandidateSnapshotMapperTest {

    @Test
    @DisplayName("candidate response를 evaluation snapshot으로 변환한다")
    void mapsCandidateResponseToEvaluationSnapshot() {
        AnalysisCandidateResponse response = new AnalysisCandidateResponse(
                List.of(),
                List.of(),
                List.of(
                        new AnalysisCandidateResponse.MissingKeywordCandidate("Spring Boot", "qualification", "Spring Boot 실무 경험"),
                        new AnalysisCandidateResponse.MissingKeywordCandidate("ignored", "unknown", "N/A")
                )
        );

        EvaluationCandidateSnapshot snapshot = EvaluationCandidateSnapshotMapper.from(response);

        assertThat(snapshot.missingKeywordCandidates())
                .extracting(candidate -> candidate.keyword() + ":" + candidate.source().name())
                .containsExactly("Spring Boot:QUALIFICATION");
    }
}
