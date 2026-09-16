package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewedFewShotAnalysisValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String VALID = """
            {"keyStrengths":[{"title":"역량","quote":"원문"}],
             "missingKeywords":[{"keyword":"API","source":"mainTask"}],
             "questionAnalyses":[{"questionId":1,"sentence":"원문","status":"proven",
              "reason":"구체적인 행동","improvement":null}]}
            """;

    @Test
    void acceptsPartialAnalysisWithoutInventingScores() throws Exception {
        assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(VALID))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "[]", "null",
            "{\"keyStrengths\":[],\"missingKeywords\":[],\"questionAnalyses\":{}}"
    })
    void rejectsMissingOrWrongContainers(String json) throws Exception {
        assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(json))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "reason", "sentence", "questionId", "improvement"})
    void rejectsMissingSentenceFields(String field) throws Exception {
        var node = mapper.readTree(VALID);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("questionAnalyses").get(0)).remove(field);
        assertThat(ReviewedFewShotAnalysisValidator.isValid(node)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "PROVEN", "", "unknown"})
    void rejectsUnsupportedSentenceStatus(String status) throws Exception {
        var node = mapper.readTree(VALID);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("questionAnalyses").get(0)).put("status", status);
        assertThat(ReviewedFewShotAnalysisValidator.isValid(node)).isFalse();
    }

    @Test
    void rejectsWrongIdAndMissingKeywordSource() throws Exception {
        assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(VALID.replace("\"questionId\":1", "\"questionId\":\"1\"")))).isFalse();
        assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(VALID.replace("\"questionId\":1", "\"questionId\":0")))).isFalse();
        assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(VALID.replace("\"mainTask\"", "\"unknown\"")))).isFalse();
        assertThat(ReviewedFewShotAnalysisValidator.isValid(mapper.readTree(VALID.replace("\"improvement\":null", "\"improvement\":123")))).isFalse();
    }
}
