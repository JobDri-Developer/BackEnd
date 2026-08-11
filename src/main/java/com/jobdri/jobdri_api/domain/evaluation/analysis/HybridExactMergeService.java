package com.jobdri.jobdri_api.domain.evaluation.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
class HybridExactMergeService {
    private static final List<String> REQUIRED_SINGLE_PASS_HEADERS = List.of(
            "caseId",
            "aiScore",
            "aiJobFit",
            "aiImpact",
            "aiCompleteness",
            "aiFeedback",
            "aiMissingKeywordsJson",
            "aiQuestionAnalysesJson",
            "rawLlmResponseJson",
            "errorMessage",
            "createdAt"
    );
    private static final List<String> REQUIRED_TWO_PASS_HEADERS = List.of(
            "caseId",
            "aiMissingKeywordsJson",
            "rawLlmResponseJson",
            "errorMessage"
    );

    private final ObjectMapper objectMapper;

    HybridExactMergeSummary merge(Path singlePassInput, Path twoPassInput, Path output) throws IOException {
        validateDifferentFiles(singlePassInput, twoPassInput, "single-pass input and two-pass input");
        validateDifferentFiles(singlePassInput, output, "single-pass input");
        validateDifferentFiles(twoPassInput, output, "two-pass input");

        List<String> singleHeaders = EvaluationCsvSupport.readHeaders(singlePassInput);
        List<String> twoPassHeaders = EvaluationCsvSupport.readHeaders(twoPassInput);
        validateHeaders("single-pass", singleHeaders, REQUIRED_SINGLE_PASS_HEADERS);
        validateHeaders("two-pass", twoPassHeaders, REQUIRED_TWO_PASS_HEADERS);

        List<Map<String, String>> singleRows = EvaluationCsvSupport.read(singlePassInput);
        List<Map<String, String>> twoPassRows = EvaluationCsvSupport.read(twoPassInput);
        Map<String, Map<String, String>> singleByCaseId = indexByCaseId("single-pass", singleRows);
        Map<String, Map<String, String>> twoPassByCaseId = indexByCaseId("two-pass", twoPassRows);
        validateCaseIdSets(singleByCaseId.keySet(), twoPassByCaseId.keySet());

        List<Map<String, String>> mergedRows = new ArrayList<>();
        for (Map<String, String> singleRow : singleRows) {
            String caseId = value(singleRow, "caseId");
            Map<String, String> twoPassRow = twoPassByCaseId.get(caseId);
            validateSuccessRow("single-pass", caseId, singleRow);
            validateSuccessRow("two-pass", caseId, twoPassRow);
            validateJson("single-pass aiQuestionAnalysesJson", caseId, value(singleRow, "aiQuestionAnalysesJson"));
            validateJson("single-pass rawLlmResponseJson", caseId, value(singleRow, "rawLlmResponseJson"));
            validateJson("single-pass aiMissingKeywordsJson", caseId, value(singleRow, "aiMissingKeywordsJson"));
            validateJson("two-pass aiMissingKeywordsJson", caseId, value(twoPassRow, "aiMissingKeywordsJson"));
            validateJson("two-pass rawLlmResponseJson", caseId, value(twoPassRow, "rawLlmResponseJson"));

            Map<String, String> merged = new LinkedHashMap<>(singleRow);
            merged.put("aiMissingKeywordsJson", value(twoPassRow, "aiMissingKeywordsJson"));
            merged.put("createdAt", createdAt());
            mergedRows.add(merged);
        }

        EvaluationCsvSupport.writeRows(output, singleHeaders, mergedRows);
        log.info(
                "Hybrid exact offline merge completed. singlePassCases={}, twoPassCases={}, mergedCases={}, output={}",
                singleRows.size(),
                twoPassRows.size(),
                mergedRows.size(),
                output
        );
        return new HybridExactMergeSummary(singleRows.size(), twoPassRows.size(), mergedRows.size(), output);
    }

    private void validateHeaders(String source, List<String> headers, List<String> requiredHeaders) {
        Set<String> headerSet = new HashSet<>(headers);
        List<String> missing = requiredHeaders.stream()
                .filter(header -> !headerSet.contains(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(source + " CSV missing required headers: " + missing);
        }
    }

    private Map<String, Map<String, String>> indexByCaseId(String source, List<Map<String, String>> rows) {
        Map<String, Map<String, String>> indexed = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String caseId = value(row, "caseId");
            if (!StringUtils.hasText(caseId)) {
                throw new IllegalArgumentException(source + " CSV has blank caseId.");
            }
            if (indexed.putIfAbsent(caseId, row) != null) {
                throw new IllegalArgumentException(source + " CSV has duplicate caseId: " + caseId);
            }
        }
        return indexed;
    }

    private void validateCaseIdSets(Set<String> singleCaseIds, Set<String> twoPassCaseIds) {
        List<String> missingInTwoPass = singleCaseIds.stream()
                .filter(caseId -> !twoPassCaseIds.contains(caseId))
                .toList();
        List<String> missingInSinglePass = twoPassCaseIds.stream()
                .filter(caseId -> !singleCaseIds.contains(caseId))
                .toList();
        if (!missingInTwoPass.isEmpty() || !missingInSinglePass.isEmpty()) {
            throw new IllegalArgumentException(
                    "Hybrid exact caseId mismatch. missingInTwoPass="
                            + missingInTwoPass
                            + ", missingInSinglePass="
                            + missingInSinglePass
            );
        }
    }

    private void validateSuccessRow(String source, String caseId, Map<String, String> row) {
        String errorMessage = value(row, "errorMessage");
        if (StringUtils.hasText(errorMessage)) {
            throw new IllegalArgumentException(source + " row has errorMessage. caseId=" + caseId);
        }
    }

    private void validateJson(String field, String caseId, String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException(field + " is blank. caseId=" + caseId);
        }
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(field + " is not valid JSON. caseId=" + caseId, e);
        }
    }

    private void validateDifferentFiles(Path input, Path output, String inputName) {
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            if ("single-pass input and two-pass input".equals(inputName)) {
                throw new IllegalArgumentException("Hybrid exact input paths must be different: " + inputName + ".");
            }
            throw new IllegalArgumentException("Hybrid exact output must not overwrite " + inputName + ".");
        }
    }

    private String value(Map<String, String> row, String key) {
        return row == null || row.get(key) == null ? "" : row.get(key).trim();
    }

    private String createdAt() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    record HybridExactMergeSummary(
            int singlePassCases,
            int twoPassCases,
            int mergedCases,
            Path outputPath
    ) {
    }
}
