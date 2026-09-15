package com.jobdri.jobdri_api.domain.evaluation.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One JSON line per input row, including failures. Contains no answer or prompt text. */
final class EvaluationFewShotSidecar implements AutoCloseable {
    private final String runId = UUID.randomUUID().toString();
    private final ObjectMapper mapper;
    private final BufferedWriter writer;
    private final Path path;

    EvaluationFewShotSidecar(Path outputCsv, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        path = outputCsv.toAbsolutePath().resolveSibling(outputCsv.getFileName() + ".fewshot." + runId + ".jsonl");
        Files.createDirectories(path.getParent());
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    Path path() {
        return path;
    }

    void write(int rowIndex, String caseId, boolean success, List<JsonNode> selections) throws IOException {
        writer.write(mapper.writeValueAsString(new Entry(1, runId, rowIndex, caseId,
                success ? "SUCCESS" : "FAILED", selections.isEmpty() ? "UNAVAILABLE" : "RECORDED",
                selections.isEmpty() ? "UNAVAILABLE" : "SELECTION_OBSERVED",
                Instant.now().toString(), List.copyOf(selections))));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private record Entry(int schemaVersion, String runId, int rowIndex, String caseId,
                         String outcome, String metadataStatus, String captureStage, String recordedAt,
                         List<JsonNode> selections) {
    }
}
