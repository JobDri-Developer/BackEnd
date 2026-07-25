package com.jobdri.jobdri_api.domain.analysis.evaluation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EvaluationCsvSupport {
    private EvaluationCsvSupport() {
    }

    static List<Map<String, String>> read(Path path) throws IOException {
        CsvRows csvRows = readRows(path);
        if (csvRows.headers().isEmpty()) {
            return List.of();
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : csvRows.rows()) {
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int j = 0; j < csvRows.headers().size(); j++) {
                values.put(csvRows.headers().get(j), j < row.size() ? row.get(j) : "");
            }
            result.add(values);
        }
        return result;
    }

    static List<String> readHeaders(Path path) throws IOException {
        return readRows(path).headers();
    }

    private static CsvRows readRows(Path path) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
        }

        List<List<String>> rows = parseRows(content.toString());
        if (rows.isEmpty()) {
            return new CsvRows(List.of(), List.of());
        }

        List<String> headers = new ArrayList<>(rows.getFirst());
        if (!headers.isEmpty() && !headers.getFirst().isEmpty() && headers.getFirst().charAt(0) == '\uFEFF') {
            headers.set(0, headers.getFirst().substring(1));
        }

        return new CsvRows(headers, rows.subList(1, rows.size()));
    }

    static void write(Path path, List<EvaluationAnalysisResult> results) throws IOException {
        writeCsv(path, List.of(
                "caseId",
                "jobCategoryMiddle",
                "jobCategorySmall",
                "mainTasks",
                "qualifications",
                "preferences",
                "question",
                "answer",
                "aiScore",
                "aiJobFit",
                "aiImpact",
                "aiCompleteness",
                "aiFeedback",
                "aiMissingKeywordsJson",
                "aiQuestionAnalysesJson",
                "rawLlmResponseJson",
                "rawCandidateResponseJson",
                "sanitizedCandidateResponseJson",
                "candidateReviewResponseJson",
                "candidateCount",
                "candidateAnalysisCount",
                "candidateStrengthCount",
                "candidateMissingKeywordCount",
                "acceptedCandidateCount",
                "rejectedCandidateCount",
                "rejectionCodeCounts",
                "finalAnalysisCount",
                "strengthCandidateCount",
                "finalStrengthCount",
                "missingKeywordCandidateCount",
                "finalMissingKeywordCount",
                "candidateCallLatencyMs",
                "finalCallLatencyMs",
                "candidateLatencyMs",
                "finalLatencyMs",
                "candidateInputTokens",
                "candidateOutputTokens",
                "finalInputTokens",
                "finalOutputTokens",
                "totalInputTokens",
                "totalOutputTokens",
                "failureStage",
                "errorMessage",
                "createdAt"
        ), writer -> {
            for (EvaluationAnalysisResult result : results == null ? List.<EvaluationAnalysisResult>of() : results) {
                writeRow(writer, List.of(
                        value(result.caseId()),
                        value(result.jobCategoryMiddle()),
                        value(result.jobCategorySmall()),
                        value(result.mainTasks()),
                        value(result.qualifications()),
                        value(result.preferences()),
                        value(result.question()),
                        value(result.answer()),
                        value(result.aiScore()),
                        value(result.aiJobFit()),
                        value(result.aiImpact()),
                        value(result.aiCompleteness()),
                        value(result.aiFeedback()),
                        value(result.aiMissingKeywordsJson()),
                        value(result.aiQuestionAnalysesJson()),
                        value(result.rawLlmResponseJson()),
                        value(result.rawCandidateResponseJson()),
                        value(result.sanitizedCandidateResponseJson()),
                        value(result.candidateReviewResponseJson()),
                        value(result.candidateCount()),
                        value(result.candidateAnalysisCount()),
                        value(result.candidateStrengthCount()),
                        value(result.candidateMissingKeywordCount()),
                        value(result.acceptedCandidateCount()),
                        value(result.rejectedCandidateCount()),
                        value(result.rejectionCodeCounts()),
                        value(result.finalAnalysisCount()),
                        value(result.strengthCandidateCount()),
                        value(result.finalStrengthCount()),
                        value(result.missingKeywordCandidateCount()),
                        value(result.finalMissingKeywordCount()),
                        value(result.candidateCallLatencyMs()),
                        value(result.finalCallLatencyMs()),
                        value(result.candidateLatencyMs()),
                        value(result.finalLatencyMs()),
                        value(result.candidateInputTokens()),
                        value(result.candidateOutputTokens()),
                        value(result.finalInputTokens()),
                        value(result.finalOutputTokens()),
                        value(result.totalInputTokens()),
                        value(result.totalOutputTokens()),
                        value(result.failureStage()),
                        value(result.errorMessage()),
                        value(result.createdAt())
                ));
            }
        });
    }

    static void writeRows(Path path, List<String> headers, List<Map<String, String>> rows) throws IOException {
        List<String> safeHeaders = headers == null ? List.of() : headers;
        writeCsv(path, safeHeaders, writer -> {
            for (Map<String, String> row : rows == null ? List.<Map<String, String>>of() : rows) {
                List<String> values = new ArrayList<>();
                for (String header : safeHeaders) {
                    values.add(value(row.get(header)));
                }
                writeRow(writer, values);
            }
        });
    }

    private static void writeCsv(Path path, List<String> headers, CsvRowsWriter rowsWriter) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeRow(writer, headers == null ? List.of() : headers);
            rowsWriter.write(writer);
        }
    }

    @FunctionalInterface
    private interface CsvRowsWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    private static List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterClosingQuote = false;

        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (inQuotes) {
                if (current == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterClosingQuote = true;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (afterClosingQuote) {
                if (current == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    afterClosingQuote = false;
                } else if (current == '\n') {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    afterClosingQuote = false;
                } else if (current == '\r') {
                    // Allow CR after a closing quote. LF, comma, or EOF will complete the field.
                } else {
                    throw new IllegalArgumentException(
                            "Malformed CSV: unexpected character after closing quote at position " + i
                    );
                }
                continue;
            }

            if (current == '"') {
                inQuotes = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (current != '\r') {
                field.append(current);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Malformed CSV: quoted field is not closed.");
        }
        row.add(field.toString());
        if (!(row.size() == 1 && row.getFirst().isEmpty() && content.endsWith("\n"))) {
            rows.add(row);
        }
        return rows;
    }

    private static void writeRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values.get(i)));
        }
        writer.newLine();
    }

    private static String escape(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record CsvRows(
            List<String> headers,
            List<List<String>> rows
    ) {
    }
}
