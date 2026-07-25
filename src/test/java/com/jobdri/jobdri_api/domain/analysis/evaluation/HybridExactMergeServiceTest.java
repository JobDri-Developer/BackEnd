package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class HybridExactMergeServiceTest {

    @TempDir
    Path tempDir;

    private final HybridExactMergeService service = new HybridExactMergeService(new ObjectMapper());

    @Test
    @DisplayName("caseId 기준으로 v5-A questionAnalyses와 two-pass missingKeywords를 exact merge한다")
    void mergeUsesSinglePassFieldsAndTwoPassMissingKeywords() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        Path output = tempDir.resolve("hybrid.csv");
        writeSinglePassCsv(single, List.of(row(
                "EV-01",
                "90",
                "[{\"keyword\":\"single\",\"source\":\"mainTask\"}]",
                "[{\"questionId\":1,\"sentence\":\"문장, 쉼표\",\"status\":\"MENTIONED\",\"reason\":\"이유\",\"improvement\":null}]",
                "{\"jobFit\":90,\"impact\":80,\"completeness\":70,\"feedback\":\"single\",\"keyStrengths\":[{\"title\":\"강점\",\"quote\":\"문장, 쉼표\"}],\"keyWeaknesses\":[],\"missingKeywords\":[],\"questionAnalyses\":[]}",
                ""
        )));
        writeTwoPassCsv(twoPass, List.of(row(
                "EV-01",
                "10",
                "[{\"keyword\":\"장애 대응 경험\",\"source\":\"qualification\"}]",
                "[]",
                "{\"jobFit\":10,\"impact\":20,\"completeness\":30,\"feedback\":\"two-pass\",\"keyStrengths\":[],\"keyWeaknesses\":[],\"missingKeywords\":[{\"keyword\":\"장애 대응 경험\",\"source\":\"qualification\"}],\"questionAnalyses\":[]}",
                ""
        )));

        HybridExactMergeService.HybridExactMergeSummary summary = service.merge(single, twoPass, output);

        assertThat(summary.singlePassCases()).isEqualTo(1);
        assertThat(summary.twoPassCases()).isEqualTo(1);
        assertThat(summary.mergedCases()).isEqualTo(1);
        List<Map<String, String>> rows = EvaluationCsvSupport.read(output);
        assertThat(rows).hasSize(1);
        Map<String, String> merged = rows.getFirst();
        assertThat(merged.get("aiScore")).isEqualTo("90");
        assertThat(merged.get("aiFeedback")).isEqualTo("single feedback");
        assertThat(merged.get("aiQuestionAnalysesJson")).contains("문장, 쉼표");
        assertThat(merged.get("aiMissingKeywordsJson")).contains("장애 대응 경험");
        assertThat(merged.get("aiMissingKeywordsJson")).doesNotContain("single");
    }

    @Test
    @DisplayName("UTF-8 BOM과 CSV quote/newline을 포함한 입력도 병합한다")
    void mergeSupportsBomAndQuotedFields() throws Exception {
        Path single = tempDir.resolve("single-bom.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        Path output = tempDir.resolve("hybrid.csv");
        Files.writeString(
                single,
                "\uFEFF" + singleHeader() + "\n"
                        + csv("EV-01") + ","
                        + csv("AI·개발·데이터") + ","
                        + csv("백엔드") + ","
                        + csv("70") + ","
                        + csv("70") + ","
                        + csv("60") + ","
                        + csv("80") + ","
                        + csv("single\nfeedback") + ","
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv("{\"jobFit\":70,\"impact\":60,\"completeness\":80,\"feedback\":\"single\\nfeedback\",\"keyStrengths\":[],\"keyWeaknesses\":[],\"missingKeywords\":[],\"questionAnalyses\":[]}") + ","
                        + csv("") + ","
                        + csv("2026-07-26T10:00:00") + "\n",
                StandardCharsets.UTF_8
        );
        writeTwoPassCsv(twoPass, List.of(row(
                "EV-01",
                "70",
                "[{\"keyword\":\"장애 대응\\n경험\",\"source\":\"qualification\"}]",
                "[]",
                "{\"jobFit\":70,\"impact\":60,\"completeness\":80,\"feedback\":\"two-pass\",\"keyStrengths\":[],\"keyWeaknesses\":[],\"missingKeywords\":[],\"questionAnalyses\":[]}",
                ""
        )));

        service.merge(single, twoPass, output);

        Map<String, String> merged = EvaluationCsvSupport.read(output).getFirst();
        assertThat(merged.get("caseId")).isEqualTo("EV-01");
        assertThat(merged.get("aiFeedback")).isEqualTo("single\nfeedback");
        assertThat(merged.get("aiMissingKeywordsJson")).contains("장애 대응\\n경험");
    }

    @Test
    @DisplayName("single-pass 또는 two-pass에 caseId가 누락되면 실패한다")
    void mergeFailsOnCaseIdMismatch() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        Path output = tempDir.resolve("hybrid.csv");
        writeSinglePassCsv(single, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));
        writeTwoPassCsv(twoPass, List.of(row("EV-02", "90", "[]", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(single, twoPass, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caseId mismatch");
    }

    @Test
    @DisplayName("중복 caseId가 있으면 실패한다")
    void mergeFailsOnDuplicateCaseId() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        Path output = tempDir.resolve("hybrid.csv");
        writeSinglePassCsv(single, List.of(
                row("EV-01", "90", "[]", "[]", validRawJson(), ""),
                row("EV-01", "80", "[]", "[]", validRawJson(), "")
        ));
        writeTwoPassCsv(twoPass, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(single, twoPass, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate caseId");
    }

    @Test
    @DisplayName("필수 JSON 필드가 파싱되지 않으면 실패한다")
    void mergeFailsOnInvalidJson() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        Path output = tempDir.resolve("hybrid.csv");
        writeSinglePassCsv(single, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));
        writeTwoPassCsv(twoPass, List.of(row("EV-01", "90", "not-json", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(single, twoPass, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two-pass aiMissingKeywordsJson is not valid JSON");
    }

    @Test
    @DisplayName("입력 row에 errorMessage가 있으면 병합하지 않는다")
    void mergeFailsOnErrorRow() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        Path output = tempDir.resolve("hybrid.csv");
        writeSinglePassCsv(single, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "single failed")));
        writeTwoPassCsv(twoPass, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(single, twoPass, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single-pass row has errorMessage")
                .hasMessageContaining("EV-01");
    }

    @Test
    @DisplayName("output이 single-pass 입력과 같으면 거부한다")
    void mergeFailsWhenOutputOverwritesSinglePassInput() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        writeSinglePassCsv(single, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));
        writeTwoPassCsv(twoPass, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(single, twoPass, single))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hybrid exact output must not overwrite single-pass input");
    }

    @Test
    @DisplayName("output이 two-pass 입력과 같으면 거부한다")
    void mergeFailsWhenOutputOverwritesTwoPassInput() throws Exception {
        Path single = tempDir.resolve("single.csv");
        Path twoPass = tempDir.resolve("two-pass.csv");
        writeSinglePassCsv(single, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));
        writeTwoPassCsv(twoPass, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(single, twoPass, twoPass))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hybrid exact output must not overwrite two-pass input");
    }

    @Test
    @DisplayName("single-pass 입력과 two-pass 입력이 같으면 거부한다")
    void mergeFailsWhenInputFilesAreSame() throws Exception {
        Path input = tempDir.resolve("same.csv");
        Path output = tempDir.resolve("hybrid.csv");
        writeSinglePassCsv(input, List.of(row("EV-01", "90", "[]", "[]", validRawJson(), "")));

        assertThatThrownBy(() -> service.merge(input, input, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hybrid exact input paths must be different")
                .hasMessageContaining("single-pass input and two-pass input");
    }

    private void writeSinglePassCsv(Path path, List<Map<String, String>> rows) throws Exception {
        EvaluationCsvSupport.writeRows(path, List.of(singleHeader().split(",")), rows);
    }

    private void writeTwoPassCsv(Path path, List<Map<String, String>> rows) throws Exception {
        EvaluationCsvSupport.writeRows(path, List.of(twoPassHeader().split(",")), rows);
    }

    private String singleHeader() {
        return "caseId,jobCategoryMiddle,jobCategorySmall,aiScore,aiJobFit,aiImpact,aiCompleteness,aiFeedback,aiMissingKeywordsJson,aiQuestionAnalysesJson,rawLlmResponseJson,errorMessage,createdAt";
    }

    private String twoPassHeader() {
        return "caseId,jobCategoryMiddle,jobCategorySmall,aiScore,aiJobFit,aiImpact,aiCompleteness,aiFeedback,aiMissingKeywordsJson,aiQuestionAnalysesJson,rawLlmResponseJson,rawCandidateResponseJson,sanitizedCandidateResponseJson,candidateReviewResponseJson,errorMessage,createdAt";
    }

    private Map<String, String> row(
            String caseId,
            String score,
            String missingKeywordsJson,
            String questionAnalysesJson,
            String rawLlmResponseJson,
            String errorMessage
    ) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("jobCategoryMiddle", "AI·개발·데이터");
        row.put("jobCategorySmall", "백엔드");
        row.put("aiScore", score);
        row.put("aiJobFit", score);
        row.put("aiImpact", "60");
        row.put("aiCompleteness", "70");
        row.put("aiFeedback", "single feedback");
        row.put("aiMissingKeywordsJson", missingKeywordsJson);
        row.put("aiQuestionAnalysesJson", questionAnalysesJson);
        row.put("rawLlmResponseJson", rawLlmResponseJson);
        row.put("rawCandidateResponseJson", "{}");
        row.put("sanitizedCandidateResponseJson", "{}");
        row.put("candidateReviewResponseJson", "{}");
        row.put("errorMessage", errorMessage);
        row.put("createdAt", "2026-07-26T10:00:00");
        return row;
    }

    private String validRawJson() {
        return "{\"jobFit\":70,\"impact\":60,\"completeness\":80,\"feedback\":\"ok\",\"keyStrengths\":[],\"keyWeaknesses\":[],\"missingKeywords\":[],\"questionAnalyses\":[]}";
    }

    private String csv(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }
}
