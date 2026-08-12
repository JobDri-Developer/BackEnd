package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateReviewSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationCandidateReviewSnapshotParserTest {

    private final EvaluationCandidateReviewSnapshotParser parser =
            new EvaluationCandidateReviewSnapshotParser(new ObjectMapper());

    @Test
    @DisplayName("candidate review json을 evaluation snapshot으로 파싱한다")
    void parsesReviewJson() {
        String json = """
                {
                  "decisions": [
                    {"accepted": true, "rejectionCode": "NONE"},
                    {"accepted": false, "rejectionCode": "NOT_ACTIONABLE"}
                  ]
                }
                """;

        EvaluationCandidateReviewSnapshot snapshot = parser.parse(json);

        assertThat(snapshot.decisions())
                .extracting(decision -> decision.accepted() + ":" + decision.rejectionCode())
                .containsExactly("true:NONE", "false:NOT_ACTIONABLE");
    }
}
