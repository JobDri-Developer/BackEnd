package com.jobdri.jobdri_api.domain.analysis.evaluation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class NlgEvaluationComparisonCsvSupport {
    private NlgEvaluationComparisonCsvSupport() {
    }

    static void write(Path path, List<NlgEvaluationComparisonResult> results) throws IOException {
        Path resolvedPath = path.toAbsolutePath().normalize();
        Path parent = resolvedPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = Files.createTempFile(parent, resolvedPath.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    tempPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
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
                        "noAnalysisAppropriateness",
                        "strengthsPrecision",
                        "strengthsCoverage",
                        "missingKeywordsPrecision",
                        "missingKeywordsCoverage",
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
                            value(result.noAnalysisAppropriateness()),
                            value(result.strengthsPrecision()),
                            value(result.strengthsCoverage()),
                            value(result.missingKeywordsPrecision()),
                            value(result.missingKeywordsCoverage()),
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
                writer.flush();
            }
            Files.move(tempPath, resolvedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempPath);
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
