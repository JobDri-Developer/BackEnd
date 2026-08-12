package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationLlmSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationLlmSnapshotMapperTest {

    @Test
    @DisplayName("runtime llm response를 evaluation snapshot으로 변환한다")
    void mapsAnalysisLlmResponseToEvaluationSnapshot() {
        AnalysisLlmResponse response = new AnalysisLlmResponse(
                4,
                4,
                4,
                "feedback",
                List.of(
                        new AnalysisLlmResponse.HighlightItem("강점", "문장 A")
                ),
                List.of(),
                List.of(
                        new AnalysisLlmResponse.MissingKeywordItem("Spring Boot", "mainTask"),
                        new AnalysisLlmResponse.MissingKeywordItem("무시됨", "unknown")
                ),
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                1L,
                                "문장",
                                "mentioned",
                                "근거",
                                "개선"
                        )
                )
        );

        EvaluationLlmSnapshot snapshot = EvaluationLlmSnapshotMapper.from(response);

        assertThat(snapshot.keyStrengthQuotes()).containsExactly("문장 A");
        assertThat(snapshot.missingKeywords())
                .extracting(keyword -> keyword.keyword() + ":" + keyword.source().name())
                .containsExactly("Spring Boot:MAIN_TASK");
        assertThat(snapshot.questionAnalyses())
                .extracting(questionAnalysis -> questionAnalysis.sentence() + ":" + questionAnalysis.status())
                .containsExactly("문장:mentioned");
    }
}
