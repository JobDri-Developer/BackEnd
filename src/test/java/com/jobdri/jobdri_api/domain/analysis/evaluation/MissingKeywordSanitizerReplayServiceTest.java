package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissingKeywordSanitizerReplayServiceTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MissingKeywordSanitizerReplayService service = new MissingKeywordSanitizerReplayService(objectMapper);

    @Test
    @DisplayName("raw 후보를 replay CSV와 review CSV로 기록하고 기존 sanitized JSON과 일치시킨다")
    void replayWritesDecisionAndReviewCsv() throws Exception {
        Path input = tempDir.resolve("input.csv");
        Path output = tempDir.resolve("replay.csv");
        Path reviewOutput = tempDir.resolve("review.csv");
        AnalysisCandidateResponse raw = response(List.of(
                candidate("재고 관리 및 분석 경험", "MAIN_TASK"),
                candidate("SQL 활용 경험", "MAIN_TASK")
        ));
        AnalysisCandidateResponse sanitized = response(List.of(candidate("재고 관리 및 분석 경험", "MAIN_TASK")));
        writeInput(input, List.of(row("EV-01", raw, sanitized)));

        MissingKeywordSanitizerReplayService.ReplaySummary summary = service.replay(input, output, reviewOutput);

        List<Map<String, String>> replayRows = EvaluationCsvSupport.read(output);
        assertThat(replayRows).hasSize(2);
        assertThat(replayRows.getFirst().get("accepted")).isEqualTo("true");
        assertThat(replayRows.getFirst().get("rejectionReason")).isEqualTo("ACCEPTED");
        assertThat(replayRows.get(1).get("accepted")).isEqualTo("false");
        assertThat(replayRows.get(1).get("rejectionReason")).isEqualTo("NOT_RELATED_TO_JD");
        assertThat(EvaluationCsvSupport.read(reviewOutput)).hasSize(2);
        assertThat(Files.isRegularFile(summary.summaryOutput())).isTrue();
        assertThat(summary.rawCandidateCount()).isEqualTo(2);
        assertThat(summary.acceptedCandidateCount()).isEqualTo(1);
        assertThat(summary.rejectedCandidateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("raw 후보가 없는 case도 summary totalCases에 포함한다")
    void replaySummaryIncludesCasesWithoutRawCandidates() throws Exception {
        Path input = tempDir.resolve("input.csv");
        Path output = tempDir.resolve("replay.csv");
        Path reviewOutput = tempDir.resolve("review.csv");
        writeInput(input, List.of(row("EV-01", response(List.of()), response(List.of()))));

        MissingKeywordSanitizerReplayService.ReplaySummary summary = service.replay(input, output, reviewOutput);

        assertThat(summary.totalCases()).isEqualTo(1);
        assertThat(summary.rawCandidateCount()).isZero();
        assertThat(EvaluationCsvSupport.read(output)).isEmpty();
        assertThat(EvaluationCsvSupport.read(summary.summaryOutput()).getFirst().get("totalCases")).isEqualTo("1");
    }

    @Test
    @DisplayName("기존 sanitized JSON과 replay accepted가 다르면 fail-fast 한다")
    void replayFailsWhenExistingSanitizedDoesNotMatch() throws Exception {
        Path input = tempDir.resolve("input.csv");
        AnalysisCandidateResponse raw = response(List.of(candidate("재고 관리 및 분석 경험", "MAIN_TASK")));
        writeInput(input, List.of(row("EV-01", raw, response(List.of()))));

        assertThatThrownBy(() -> service.replay(input, tempDir.resolve("replay.csv"), tempDir.resolve("review.csv")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepted candidates mismatch")
                .hasMessageContaining("EV-01");
    }

    @Test
    @DisplayName("malformed rawCandidateResponseJson은 fail-fast 한다")
    void malformedRawCandidateResponseFailsFast() throws Exception {
        Path input = tempDir.resolve("input.csv");
        Map<String, String> row = row("EV-01", response(List.of()), response(List.of()));
        row.put("rawCandidateResponseJson", "{bad-json");
        EvaluationCsvSupport.writeRows(input, headers(), List.of(row));

        assertThatThrownBy(() -> service.replay(input, tempDir.resolve("replay.csv"), tempDir.resolve("review.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawCandidateResponseJson")
                .hasMessageContaining("EV-01");
    }

    @Test
    @DisplayName("duplicate caseId는 fail-fast 한다")
    void duplicateCaseIdFailsFast() throws Exception {
        Path input = tempDir.resolve("input.csv");
        writeInput(input, List.of(
                row("EV-01", response(List.of()), response(List.of())),
                row("EV-01", response(List.of()), response(List.of()))
        ));

        assertThatThrownBy(() -> service.replay(input, tempDir.resolve("replay.csv"), tempDir.resolve("review.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate caseId")
                .hasMessageContaining("EV-01");
    }

    @Test
    @DisplayName("UTF-8 BOM과 쉼표, 줄바꿈, 큰따옴표가 포함된 후보를 처리한다")
    void replayHandlesBomAndEscapedCsvValues() throws Exception {
        Path input = tempDir.resolve("input.csv");
        AnalysisCandidateResponse raw = response(List.of(candidate("SQL, 활용 \"경험\"\n", "MAIN_TASK")));
        writeInput(input, List.of(row("EV-01", raw, response(List.of()))));
        String content = Files.readString(input);
        Files.writeString(input, "\uFEFF" + content);

        MissingKeywordSanitizerReplayService.ReplaySummary summary =
                service.replay(input, tempDir.resolve("replay.csv"), tempDir.resolve("review.csv"));

        assertThat(summary.rawCandidateCount()).isEqualTo(1);
    }

    private void writeInput(Path input, List<Map<String, String>> rows) throws Exception {
        EvaluationCsvSupport.writeRows(input, headers(), rows);
    }

    private List<String> headers() {
        return List.of(
                "caseId",
                "mainTasks",
                "qualifications",
                "answer",
                "rawCandidateResponseJson",
                "sanitizedCandidateResponseJson"
        );
    }

    private Map<String, String> row(
            String caseId,
            AnalysisCandidateResponse raw,
            AnalysisCandidateResponse sanitized
    ) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("mainTasks", "재고 현황을 분석하고 적정 재고를 관리합니다.");
        row.put("qualifications", "영어 고객 커뮤니케이션 경험");
        row.put("answer", "재고관리및분석경험을 쌓았습니다.");
        row.put("rawCandidateResponseJson", objectMapper.writeValueAsString(raw));
        row.put("sanitizedCandidateResponseJson", objectMapper.writeValueAsString(sanitized));
        return row;
    }

    private AnalysisCandidateResponse response(List<AnalysisCandidateResponse.MissingKeywordCandidate> candidates) {
        return new AnalysisCandidateResponse(List.of(), List.of(), candidates);
    }

    private AnalysisCandidateResponse.MissingKeywordCandidate candidate(String keyword, String source) {
        return new AnalysisCandidateResponse.MissingKeywordCandidate(keyword, source, keyword);
    }
}
