package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationLlmSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationLlmSnapshotParserTest {

    private final EvaluationLlmSnapshotParser parser = new EvaluationLlmSnapshotParser(new ObjectMapper());

    @Test
    @DisplayName("raw llm response json을 evaluation snapshot으로 파싱한다")
    void parsesRawLlmResponseJson() {
        String rawLlmResponseJson = """
                {
                  "keyStrengths": [{"quote": "강점 문장"}],
                  "missingKeywords": [{"keyword": "Spring Boot", "source": "MAIN_TASK"}],
                  "questionAnalyses": [{"questionId": 1, "sentence": "문장", "status": "mentioned", "reason": "근거", "improvement": "개선"}]
                }
                """;

        EvaluationLlmSnapshot snapshot = parser.parseRawLlmResponse(rawLlmResponseJson);

        assertThat(snapshot.jobFit()).isNull();
        assertThat(snapshot.feedback()).isEmpty();
        assertThat(snapshot.keyStrengthQuotes()).containsExactly("강점 문장");
        assertThat(snapshot.missingKeywords())
                .extracting(keyword -> keyword.keyword() + ":" + keyword.source().name())
                .containsExactly("Spring Boot:MAIN_TASK");
        assertThat(snapshot.questionAnalyses())
                .extracting(questionAnalysis -> questionAnalysis.sentence() + ":" + questionAnalysis.status())
                .containsExactly("문장:mentioned");
    }

    @Test
    @DisplayName("missing keyword json을 evaluation 모델로 파싱한다")
    void parsesMissingKeywordJson() {
        List<?> missingKeywords = parser.parseMissingKeywords(
                "[{\"keyword\":\"Spring Boot\",\"source\":\"QUALIFICATIONS\"}]",
                "EV-01"
        );

        assertThat(missingKeywords)
                .extracting(keyword -> keyword.toString())
                .containsExactly("EvaluationMissingKeyword[keyword=Spring Boot, source=QUALIFICATION]");
    }

    @Test
    @DisplayName("missing keyword json이 배열이 아니면 예외를 던진다")
    void rejectsNonArrayMissingKeywordJson() {
        assertThatThrownBy(() -> parser.parseMissingKeywords("{\"keyword\":\"Spring Boot\"}", "EV-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiMissingKeywordsJson must be a JSON array");
    }
}
