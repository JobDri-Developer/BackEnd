package com.jobdri.jobdri_api.domain.analysis.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationCsvSupportTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("quoted comma, quote, newline을 포함한 CSV를 읽는다")
    void readQuotedCsv() throws Exception {
        Path input = tempDir.resolve("evaluation_cases.csv");
        Files.writeString(
                input,
                "\uFEFFcaseId,jobCategoryMiddle,answer\n"
                        + "EV-01,AI·개발·데이터,\"문장, 쉼표와 \"\"따옴표\"\"\n"
                        + "줄바꿈\"\n",
                StandardCharsets.UTF_8
        );

        List<Map<String, String>> rows = EvaluationCsvSupport.read(input);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("caseId")).isEqualTo("EV-01");
        assertThat(rows.getFirst().get("answer")).isEqualTo("문장, 쉼표와 \"따옴표\"\n줄바꿈");
    }

    @Test
    @DisplayName("평가 결과 CSV는 JSON 문자열을 안전하게 escape한다")
    void writeEscapedCsv() throws Exception {
        Path output = tempDir.resolve("evaluation_ai_results.csv");
        EvaluationCsvSupport.write(output, List.of(new EvaluationAnalysisResult(
                "EV-01",
                "AI·개발·데이터",
                "백엔드",
                80,
                80,
                80,
                80,
                "피드백, 쉼표",
                "[{\"keyword\":\"SQL\",\"source\":\"qualification\"}]",
                "[]",
                "{\"feedback\":\"문장\"}",
                "",
                "2026-07-13T10:00:00"
        )));

        String csv = Files.readString(output, StandardCharsets.UTF_8);

        assertThat(csv).contains("\"피드백, 쉼표\"");
        assertThat(csv).contains("\"[{\"\"keyword\"\":\"\"SQL\"\",\"\"source\"\":\"\"qualification\"\"}]\"");
    }

    @Test
    @DisplayName("닫히지 않은 quoted field는 파싱 예외를 던진다")
    void readRejectsUnclosedQuotedField() throws Exception {
        Path input = tempDir.resolve("evaluation_cases.csv");
        Files.writeString(
                input,
                "caseId,answer\nEV-01,\"닫히지 않은 문장\n",
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> EvaluationCsvSupport.read(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quoted field is not closed");
    }

    @Test
    @DisplayName("닫힌 quote 뒤 delimiter가 아닌 문자가 오면 파싱 예외를 던진다")
    void readRejectsUnexpectedCharacterAfterClosingQuote() throws Exception {
        Path input = tempDir.resolve("evaluation_cases.csv");
        Files.writeString(
                input,
                "caseId,answer\nEV-01,\"문장\"invalid\n",
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> EvaluationCsvSupport.read(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected character after closing quote");
    }
}
