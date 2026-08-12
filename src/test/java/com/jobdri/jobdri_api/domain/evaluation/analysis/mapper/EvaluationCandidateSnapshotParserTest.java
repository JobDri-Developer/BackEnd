package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationCandidateSnapshotParserTest {

    private final EvaluationCandidateSnapshotParser parser = new EvaluationCandidateSnapshotParser(new ObjectMapper());

    @Test
    @DisplayName("candidate response json을 evaluation snapshot으로 파싱한다")
    void parsesCandidateResponseJson() {
        String json = """
                {
                  "strengthCandidates": [{"quote": "강점"}],
                  "analysisCandidates": [{"candidateId": "candidate-1"}],
                  "missingKeywordCandidates": [
                    {"keyword": "Spring Boot", "source": "qualification", "relatedRequirement": "Spring Boot 실무 경험"},
                    {"keyword": "ignored", "source": "unknown", "relatedRequirement": "N/A"}
                  ]
                }
                """;

        EvaluationCandidateSnapshot snapshot = parser.parse(json, "rawCandidateResponseJson", "EV-01");

        assertThat(snapshot.strengthCandidates()).hasSize(1);
        assertThat(snapshot.strengthCandidates())
                .extracting(node -> ((JsonNode) node).path("quote").asText())
                .containsExactly("강점");
        assertThat(snapshot.analysisCandidates()).hasSize(1);
        assertThat(snapshot.analysisCandidates())
                .extracting(node -> ((JsonNode) node).path("candidateId").asText())
                .containsExactly("candidate-1");
        assertThat(snapshot.missingKeywordCandidates())
                .extracting(candidate -> candidate.keyword() + ":" + candidate.source().name())
                .containsExactly("Spring Boot:QUALIFICATION");
    }

    @Test
    @DisplayName("candidate response json이 malformed면 caseId를 포함해 fail-fast 한다")
    void rejectsMalformedCandidateResponseJson() {
        assertThatThrownBy(() -> parser.parse("{bad-json", "rawCandidateResponseJson", "EV-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawCandidateResponseJson")
                .hasMessageContaining("EV-01");
    }

    @Test
    @DisplayName("candidate 배열 필드가 없거나 배열이 아니면 빈 목록으로 파싱한다")
    void returnsEmptyListsWhenCandidateArraysMissingOrNotArray() {
        EvaluationCandidateSnapshot missingArrays = parser.parse("{}", "rawCandidateResponseJson", "EV-01");
        EvaluationCandidateSnapshot nonArrayFields = parser.parse("""
                {
                  "strengthCandidates": {},
                  "analysisCandidates": "",
                  "missingKeywordCandidates": 1
                }
                """, "rawCandidateResponseJson", "EV-01");

        assertThat(missingArrays.strengthCandidates()).isEmpty();
        assertThat(missingArrays.analysisCandidates()).isEmpty();
        assertThat(missingArrays.missingKeywordCandidates()).isEmpty();
        assertThat(nonArrayFields.strengthCandidates()).isEmpty();
        assertThat(nonArrayFields.analysisCandidates()).isEmpty();
        assertThat(nonArrayFields.missingKeywordCandidates()).isEmpty();
    }
}
