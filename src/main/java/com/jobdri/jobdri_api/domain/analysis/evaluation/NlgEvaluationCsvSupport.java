package com.jobdri.jobdri_api.domain.analysis.evaluation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class NlgEvaluationCsvSupport {
    private NlgEvaluationCsvSupport() {
    }

    static void write(Path path, List<NlgEvaluationResult> results) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeRow(writer, List.of(
                    "caseId",
                    "sourceResultFile",
                    "analysisCount",
                    "averageRelevance",
                    "averageProblemValidity",
                    "averageSentenceTypeConsistency",
                    "averageReasonCorrectness",
                    "averageContextAwareness",
                    "averageFaithfulness",
                    "averageTenseConsistency",
                    "averageUsability",
                    "averageNonMeta",
                    "averageMeaningPreservation",
                    "noAnalysisAppropriateness",
                    "strengthsPrecision",
                    "strengthsCoverage",
                    "missingKeywordsPrecision",
                    "missingKeywordsCoverage",
                    "overallUsefulness",
                    "errorCodes",
                    "shortRationale",
                    "judgeInputTokens",
                    "judgeOutputTokens",
                    "judgeLatencyMs",
                    "failureStage"
            ));
            for (NlgEvaluationResult result : results) {
                writeRow(writer, List.of(
                        value(result.caseId()),
                        value(result.sourceResultFile()),
                        value(result.analysisCount()),
                        value(result.averageRelevance()),
                        value(result.averageProblemValidity()),
                        value(result.averageSentenceTypeConsistency()),
                        value(result.averageReasonCorrectness()),
                        value(result.averageContextAwareness()),
                        value(result.averageFaithfulness()),
                        value(result.averageTenseConsistency()),
                        value(result.averageUsability()),
                        value(result.averageNonMeta()),
                        value(result.averageMeaningPreservation()),
                        value(result.noAnalysisAppropriateness()),
                        value(result.strengthsPrecision()),
                        value(result.strengthsCoverage()),
                        value(result.missingKeywordsPrecision()),
                        value(result.missingKeywordsCoverage()),
                        value(result.overallUsefulness()),
                        value(result.errorCodes()),
                        value(result.shortRationale()),
                        value(result.judgeInputTokens()),
                        value(result.judgeOutputTokens()),
                        value(result.judgeLatencyMs()),
                        value(result.failureStage())
                ));
            }
        }
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
}
