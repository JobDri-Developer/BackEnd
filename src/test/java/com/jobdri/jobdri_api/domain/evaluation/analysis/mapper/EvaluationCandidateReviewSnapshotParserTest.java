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

    @Test
    @DisplayName("candidate review json이 비어 있거나 malformed면 empty snapshot을 반환한다")
    void returnsEmptySnapshotForEmptyOrMalformedJson() {
        assertThat(parser.parse(null).decisions()).isEmpty();
        assertThat(parser.parse("").decisions()).isEmpty();
        assertThat(parser.parse("{bad-json").decisions()).isEmpty();
    }

    @Test
    @DisplayName("decisions가 없거나 배열이 아니면 empty snapshot을 반환한다")
    void returnsEmptySnapshotWhenDecisionsMissingOrNotArray() {
        assertThat(parser.parse("{\"other\":[]}").decisions()).isEmpty();
        assertThat(parser.parse("{\"decisions\":{}}").decisions()).isEmpty();
    }

    @Test
    @DisplayName("accepted가 boolean이 아니면 null로 파싱한다")
    void parsesNonBooleanAcceptedAsNull() {
        EvaluationCandidateReviewSnapshot snapshot = parser.parse("""
                {
                  "decisions": [
                    {"accepted": "true", "rejectionCode": "NONE"}
                  ]
                }
                """);

        assertThat(snapshot.decisions()).singleElement()
                .extracting(decision -> decision.accepted() + ":" + decision.rejectionCode())
                .isEqualTo("null:NONE");
    }
}
