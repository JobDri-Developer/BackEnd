package com.jobdri.jobdri_api.domain.analysis.evaluation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class NlgEvaluationComparisonCsvSupport {
    private NlgEvaluationComparisonCsvSupport() {
    }

    static void write(Path path, List<NlgEvaluationComparisonResult> results) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeRow(writer, List.of(
                    "sourceResultFile",
                    "caseCount",
                    "successCount",
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
                    "strengthsPrecision",
                    "missingKeywordsPrecision",
                    "overallUsefulness",
                    "averageJudgeInputTokens",
                    "averageJudgeOutputTokens",
                    "averageJudgeLatencyMs",
                    "averageAnalysisCount",
                    "metaImprovementRate",
                    "unsupportedFactRate",
                    "falsePositiveAnalysisRate",
                    "fatalErrorRate",
                    "errorCodeCounts"
            ));
            for (NlgEvaluationComparisonResult result : results) {
                writeRow(writer, List.of(
                        value(result.sourceResultFile()),
                        value(result.caseCount()),
                        value(result.successCount()),
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
                        value(result.strengthsPrecision()),
                        value(result.missingKeywordsPrecision()),
                        value(result.overallUsefulness()),
                        value(result.averageJudgeInputTokens()),
                        value(result.averageJudgeOutputTokens()),
                        value(result.averageJudgeLatencyMs()),
                        value(result.averageAnalysisCount()),
                        value(result.metaImprovementRate()),
                        value(result.unsupportedFactRate()),
                        value(result.falsePositiveAnalysisRate()),
                        value(result.fatalErrorRate()),
                        value(result.errorCodeCounts())
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
