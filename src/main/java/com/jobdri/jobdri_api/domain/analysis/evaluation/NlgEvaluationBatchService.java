package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class NlgEvaluationBatchService {
    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;
    private static final int MAX_SHORT_RATIONALE_LENGTH = 300;
    private static final String DEFAULT_SOURCE_CASES_FILE = "evaluation_cases_reviewed.csv";
    private static final Map<String, List<String>> HEADER_ALIASES = Map.of(
            "caseId", List.of("caseId"),
            "mainTasks", List.of("mainTasks"),
            "qualifications", List.of("qualifications"),
            "preferences", List.of("preferences"),
            "question", List.of("question"),
            "answer", List.of("answer"),
            "aiQuestionAnalysesJson", List.of("aiQuestionAnalysesJson", "questionAnalysesJson"),
            "aiMissingKeywordsJson", List.of("aiMissingKeywordsJson", "missingKeywordsJson"),
            "rawLlmResponseJson", List.of("rawLlmResponseJson")
    );
    private static final List<String> INPUT_JSON_HEADERS = List.of(
            "inputJson",
            "sourceInputJson",
            "evaluationInputJson"
    );
    private static final List<String> REQUIRED_LOGICAL_FIELDS = List.of(
            "caseId",
            "mainTasks",
            "qualifications",
            "question",
            "answer",
            "aiQuestionAnalysesJson",
            "aiMissingKeywordsJson",
            "rawLlmResponseJson"
    );

    private final NlgEvaluationAiClient nlgEvaluationAiClient;
    private final ObjectMapper objectMapper;

    NlgEvaluationSummary run(Path inputPath, Path outputPath) throws IOException {
        validateDifferentFiles(inputPath, outputPath);
        List<String> inputHeaders = EvaluationCsvSupport.readHeaders(inputPath);
        List<Map<String, String>> rows = EvaluationCsvSupport.read(inputPath);
        SourceCaseRows sourceCaseRows = loadSourceCaseRowsIfNeeded(inputPath, inputHeaders);
        ResolvedHeaders resolvedHeaders = resolveHeaders(inputHeaders, sourceCaseRows.headers());
        validateHeaders(inputPath, resolvedHeaders, sourceCaseRows);
        log.info("NLG judge input headers resolved. {}", resolvedHeaders.logSummary());

        List<NlgEvaluationResult> results = new ArrayList<>();
        int successCount = 0;

        for (Map<String, String> row : rows) {
            String caseId = resolvedValue(row, sourceCaseRows, resolvedHeaders, "caseId");
            try {
                NlgEvaluationAiClient.NlgJudgeInput input = buildJudgeInput(inputPath, row, sourceCaseRows, resolvedHeaders);
                NlgEvaluationAiClient.JudgeCallResult callResult = nlgEvaluationAiClient.evaluate(input);
                NlgEvaluationResult result = validateAndBuildResult(input, callResult);
                results.add(result);
                if (!StringUtils.hasText(result.failureStage())) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("NLG judge failed. caseId={}, message={}", caseId, e.getMessage());
                results.add(NlgEvaluationResult.failed(caseId, inputPath.toString(), failureStage(e)));
            }
        }

        NlgEvaluationCsvSupport.write(outputPath, results);
        return new NlgEvaluationSummary(rows.size(), successCount, rows.size() - successCount, outputPath);
    }

    NlgEvaluationComparisonSummary compare(List<Path> inputPaths, Path outputPath) throws IOException {
        validateComparisonInputs(inputPaths);
        Path resolvedOutputPath = outputPath.toAbsolutePath().normalize();
        validateComparisonOutput(inputPaths, resolvedOutputPath);

        List<NlgEvaluationComparisonResult> results = new ArrayList<>();
        for (Path inputPath : inputPaths) {
            List<Map<String, String>> rows = EvaluationCsvSupport.read(inputPath);
            results.add(buildComparisonResult(inputPath, rows));
        }

        NlgEvaluationComparisonCsvSupport.write(resolvedOutputPath, results);
        int actualSummaryRows = EvaluationCsvSupport.read(resolvedOutputPath).size();
        long sizeBytes = validateCreatedOutputFile(
                resolvedOutputPath,
                "NLG judge comparison output",
                actualSummaryRows,
                inputPaths.size()
        );
        return new NlgEvaluationComparisonSummary(inputPaths.size(), resolvedOutputPath, actualSummaryRows, sizeBytes);
    }

    private NlgEvaluationAiClient.NlgJudgeInput buildJudgeInput(
            Path inputPath,
            Map<String, String> row,
            SourceCaseRows sourceCaseRows,
            ResolvedHeaders resolvedHeaders
    ) {
        String caseId = resolvedValue(row, sourceCaseRows, resolvedHeaders, "caseId");
        String questionAnalysesJson = resolvedValue(row, sourceCaseRows, resolvedHeaders, "aiQuestionAnalysesJson");
        List<EvaluationQuestionAnalysisResult> questionAnalyses = readQuestionAnalyses(questionAnalysesJson);
        return new NlgEvaluationAiClient.NlgJudgeInput(
                caseId,
                inputPath.toString(),
                requiredResolvedValue(row, sourceCaseRows, resolvedHeaders, "mainTasks", caseId),
                requiredResolvedValue(row, sourceCaseRows, resolvedHeaders, "qualifications", caseId),
                resolvedValue(row, sourceCaseRows, resolvedHeaders, "preferences"),
                requiredResolvedValue(row, sourceCaseRows, resolvedHeaders, "question", caseId),
                requiredResolvedValue(row, sourceCaseRows, resolvedHeaders, "answer", caseId),
                questionAnalysesJson,
                readKeyStrengthsJson(resolvedValue(row, sourceCaseRows, resolvedHeaders, "rawLlmResponseJson")),
                resolvedValue(row, sourceCaseRows, resolvedHeaders, "aiMissingKeywordsJson"),
                value(row, "rawCandidateResponseJson"),
                value(row, "candidateReviewResponseJson"),
                questionAnalyses
        );
    }

    private NlgEvaluationResult validateAndBuildResult(
            NlgEvaluationAiClient.NlgJudgeInput input,
            NlgEvaluationAiClient.JudgeCallResult callResult
    ) {
        NlgEvaluationResponse response = callResult.response();
        if (response == null || !Objects.equals(input.caseId(), response.caseId())) {
            return NlgEvaluationResult.failed(input.caseId(), input.sourceResultFile(), "judge_validation_failed");
        }

        if (input.questionAnalyses().isEmpty()
                && response.questionAnalysisEvaluations() != null
                && !response.questionAnalysisEvaluations().isEmpty()) {
            return NlgEvaluationResult.failed(input.caseId(), input.sourceResultFile(), "judge_validation_failed");
        }

        List<NlgEvaluationResponse.QuestionAnalysisEvaluation> evaluations =
                validQuestionEvaluations(input.questionAnalyses(), response.questionAnalysisEvaluations());
        List<NlgEvaluationErrorCode> errorCodes = mergeErrorCodes(response, evaluations);
        boolean hasFatalError = hasFatalError(errorCodes);
        return new NlgEvaluationResult(
                input.caseId(),
                input.sourceResultFile(),
                input.questionAnalyses().size(),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::relevance),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::problemValidity),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::sentenceTypeConsistency),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::reasonCorrectness),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::contextAwareness),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::faithfulness),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::tenseConsistency),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::usability),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::nonMeta),
                average(evaluations, NlgEvaluationResponse.QuestionAnalysisEvaluation::meaningPreservation),
                validCaseScore(response.noAnalysisAppropriateness()),
                validCaseScore(response.strengthsPrecision()),
                validCaseScore(response.strengthsCoverage()),
                validCaseScore(response.missingKeywordsPrecision()),
                validCaseScore(response.missingKeywordsCoverage()),
                validOverallUsefulness(response.overallUsefulness(), hasFatalError),
                writeJson(errorCodes.stream().map(Enum::name).toList()),
                truncateShortRationale(response.shortRationale()),
                callResult.inputTokens(),
                callResult.outputTokens(),
                callResult.latencyMs(),
                ""
        );
    }

    private List<NlgEvaluationResponse.QuestionAnalysisEvaluation> validQuestionEvaluations(
            List<EvaluationQuestionAnalysisResult> sourceAnalyses,
            List<NlgEvaluationResponse.QuestionAnalysisEvaluation> evaluations
    ) {
        if (sourceAnalyses == null || sourceAnalyses.isEmpty() || evaluations == null || evaluations.isEmpty()) {
            return List.of();
        }

        List<NlgEvaluationResponse.QuestionAnalysisEvaluation> valid = new ArrayList<>();
        Set<Integer> seenIndexes = new HashSet<>();
        for (NlgEvaluationResponse.QuestionAnalysisEvaluation evaluation : evaluations) {
            if (evaluation == null || !hasValidScores(evaluation)) {
                continue;
            }

            Integer index = resolveAnalysisIndex(sourceAnalyses, evaluation);
            if (index == null || !seenIndexes.add(index)) {
                continue;
            }

            valid.add(new NlgEvaluationResponse.QuestionAnalysisEvaluation(
                    index,
                    sourceAnalyses.get(index).sentence(),
                    evaluation.relevance(),
                    evaluation.problemValidity(),
                    evaluation.sentenceTypeConsistency(),
                    evaluation.reasonCorrectness(),
                    evaluation.contextAwareness(),
                    evaluation.faithfulness(),
                    evaluation.tenseConsistency(),
                    evaluation.usability(),
                    evaluation.nonMeta(),
                    evaluation.meaningPreservation(),
                    sanitizeErrorCodes(evaluation.errorCodes(), hasLowCriterionScore(evaluation))
            ));
        }
        return valid;
    }

    private Integer resolveAnalysisIndex(
            List<EvaluationQuestionAnalysisResult> sourceAnalyses,
            NlgEvaluationResponse.QuestionAnalysisEvaluation evaluation
    ) {
        if (evaluation.analysisIndex() != null
                && evaluation.analysisIndex() >= 0
                && evaluation.analysisIndex() < sourceAnalyses.size()) {
            return evaluation.analysisIndex();
        }
        if (!StringUtils.hasText(evaluation.sentence())) {
            return null;
        }
        for (int i = 0; i < sourceAnalyses.size(); i++) {
            if (Objects.equals(sourceAnalyses.get(i).sentence(), evaluation.sentence())) {
                return i;
            }
        }
        return null;
    }

    private boolean hasValidScores(NlgEvaluationResponse.QuestionAnalysisEvaluation evaluation) {
        return validScore(evaluation.relevance())
                && validScore(evaluation.problemValidity())
                && validScore(evaluation.sentenceTypeConsistency())
                && validScore(evaluation.reasonCorrectness())
                && validScore(evaluation.contextAwareness())
                && validScore(evaluation.faithfulness())
                && validScore(evaluation.tenseConsistency())
                && validScore(evaluation.usability())
                && validScore(evaluation.nonMeta())
                && validScore(evaluation.meaningPreservation());
    }

    private List<NlgEvaluationErrorCode> mergeErrorCodes(
            NlgEvaluationResponse response,
            List<NlgEvaluationResponse.QuestionAnalysisEvaluation> evaluations
    ) {
        boolean hasLowScore = hasLowCaseScore(response);
        Set<NlgEvaluationErrorCode> codes = new HashSet<>(sanitizeErrorCodes(
                response.caseErrorCodes(),
                hasLowScore
        ));
        for (NlgEvaluationResponse.QuestionAnalysisEvaluation evaluation : evaluations) {
            boolean hasLowCriterionScore = hasLowCriterionScore(evaluation);
            hasLowScore = hasLowScore || hasLowCriterionScore;
            codes.addAll(sanitizeErrorCodes(evaluation.errorCodes(), hasLowCriterionScore));
        }
        if (codes.size() > 1) {
            codes.remove(NlgEvaluationErrorCode.NONE);
        }
        if (hasLowScore && codes.size() == 1 && codes.contains(NlgEvaluationErrorCode.NONE)) {
            return List.of();
        }
        if (codes.isEmpty()) {
            if (hasLowScore) {
                return List.of();
            }
            return List.of(NlgEvaluationErrorCode.NONE);
        }
        return codes.stream()
                .sorted()
                .toList();
    }

    private List<NlgEvaluationErrorCode> sanitizeErrorCodes(
            List<NlgEvaluationErrorCode> errorCodes,
            boolean hasLowScore
    ) {
        if (errorCodes == null || errorCodes.isEmpty()) {
            return List.of();
        }
        List<NlgEvaluationErrorCode> sanitized = errorCodes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        boolean hasNonNone = sanitized.stream().anyMatch(code -> code != NlgEvaluationErrorCode.NONE);
        if (hasNonNone) {
            return sanitized.stream()
                    .filter(code -> code != NlgEvaluationErrorCode.NONE)
                    .toList();
        }
        if (hasLowScore) {
            return List.of();
        }
        return sanitized;
    }

    private Double average(
            List<NlgEvaluationResponse.QuestionAnalysisEvaluation> evaluations,
            java.util.function.Function<NlgEvaluationResponse.QuestionAnalysisEvaluation, Integer> extractor
    ) {
        if (evaluations == null || evaluations.isEmpty()) {
            return null;
        }
        java.util.OptionalDouble average = evaluations.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        if (average.isEmpty()) {
            return null;
        }
        return Math.round(average.getAsDouble() * 100.0) / 100.0;
    }

    private Integer validCaseScore(Integer score) {
        return validScore(score) ? score : null;
    }

    private Integer validOverallUsefulness(Integer score, boolean hasFatalError) {
        if (!validScore(score)) {
            return null;
        }
        if (hasFatalError && score >= 4) {
            return null;
        }
        return score;
    }

    private boolean validScore(Integer score) {
        return score != null && score >= MIN_SCORE && score <= MAX_SCORE;
    }

    private boolean hasLowCriterionScore(NlgEvaluationResponse.QuestionAnalysisEvaluation evaluation) {
        return lowScore(evaluation.problemValidity())
                || lowScore(evaluation.contextAwareness())
                || lowScore(evaluation.sentenceTypeConsistency())
                || lowScore(evaluation.faithfulness())
                || lowScore(evaluation.tenseConsistency())
                || lowScore(evaluation.nonMeta());
    }

    private boolean hasLowCaseScore(NlgEvaluationResponse response) {
        return lowScore(response.noAnalysisAppropriateness())
                || lowScore(response.strengthsPrecision())
                || lowScore(response.strengthsCoverage())
                || lowScore(response.missingKeywordsPrecision())
                || lowScore(response.missingKeywordsCoverage())
                || lowScore(response.overallUsefulness());
    }

    private boolean lowScore(Integer score) {
        return score != null && score <= 2;
    }

    private boolean hasFatalError(List<NlgEvaluationErrorCode> errorCodes) {
        return errorCodes.stream().anyMatch(code -> Set.of(
                NlgEvaluationErrorCode.UNSUPPORTED_FACT,
                NlgEvaluationErrorCode.TENSE_CHANGED,
                NlgEvaluationErrorCode.FALSE_POSITIVE_ANALYSIS,
                NlgEvaluationErrorCode.INVALID_MISSING_KEYWORD
        ).contains(code));
    }

    private List<EvaluationQuestionAnalysisResult> readQuestionAnalyses(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<EvaluationQuestionAnalysisResult> values = objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    }
            );
            return values == null ? List.of() : values;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String readKeyStrengthsJson(String rawLlmResponseJson) {
        if (!StringUtils.hasText(rawLlmResponseJson)) {
            return "[]";
        }
        try {
            AnalysisLlmResponse response = objectMapper.readValue(rawLlmResponseJson, AnalysisLlmResponse.class);
            return writeJson(response == null || response.keyStrengths() == null ? List.of() : response.keyStrengths());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private SourceCaseRows loadSourceCaseRowsIfNeeded(Path inputPath, List<String> inputHeaders) throws IOException {
        boolean hasAllContextHeaders = List.of("mainTasks", "qualifications", "question", "answer").stream()
                .allMatch(field -> directHeader(inputHeaders, field).isPresent());
        if (hasAllContextHeaders) {
            return SourceCaseRows.empty();
        }

        Path sourcePath = inputPath.toAbsolutePath().normalize().getParent();
        if (sourcePath == null) {
            return SourceCaseRows.empty();
        }
        sourcePath = sourcePath.resolve(DEFAULT_SOURCE_CASES_FILE);
        if (!java.nio.file.Files.isRegularFile(sourcePath)) {
            return SourceCaseRows.empty();
        }

        List<String> sourceHeaders = EvaluationCsvSupport.readHeaders(sourcePath);
        Map<String, Map<String, String>> rowsByCaseId = EvaluationCsvSupport.read(sourcePath).stream()
                .filter(row -> StringUtils.hasText(value(row, "caseId")))
                .collect(Collectors.toMap(
                        row -> value(row, "caseId"),
                        row -> row,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return new SourceCaseRows(sourcePath, sourceHeaders, rowsByCaseId);
    }

    private ResolvedHeaders resolveHeaders(List<String> inputHeaders, List<String> sourceHeaders) {
        Map<String, ResolvedHeader> resolved = new LinkedHashMap<>();
        for (String logicalField : REQUIRED_LOGICAL_FIELDS) {
            resolved.put(logicalField, resolveHeader(logicalField, inputHeaders, sourceHeaders));
        }
        resolved.put("preferences", resolveHeader("preferences", inputHeaders, sourceHeaders));
        return new ResolvedHeaders(resolved);
    }

    private ResolvedHeader resolveHeader(String logicalField, List<String> inputHeaders, List<String> sourceHeaders) {
        Optional<String> inputHeader = directHeader(inputHeaders, logicalField);
        if (inputHeader.isPresent()) {
            return ResolvedHeader.input(inputHeader.get());
        }
        Optional<String> sourceHeader = directHeader(sourceHeaders, logicalField);
        if (sourceHeader.isPresent()) {
            return ResolvedHeader.source(sourceHeader.get());
        }
        if (inputHeaders != null && inputHeaders.stream().anyMatch(INPUT_JSON_HEADERS::contains)) {
            return ResolvedHeader.json(logicalField);
        }
        return ResolvedHeader.missing(logicalField);
    }

    private Optional<String> directHeader(List<String> headers, String logicalField) {
        Set<String> headerSet = new HashSet<>(headers == null ? List.of() : headers);
        return HEADER_ALIASES.getOrDefault(logicalField, List.of(logicalField)).stream()
                .filter(headerSet::contains)
                .findFirst();
    }

    private void validateHeaders(Path inputPath, ResolvedHeaders resolvedHeaders, SourceCaseRows sourceCaseRows) {
        List<String> missingHeaders = REQUIRED_LOGICAL_FIELDS.stream()
                .filter(field -> !resolvedHeaders.has(field, sourceCaseRows))
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException(
                    "NLG judge input CSV missing required headers or source data: "
                            + missingHeaders
                            + ". input="
                            + inputPath
                            + ", expected direct headers or "
                            + DEFAULT_SOURCE_CASES_FILE
                            + " with caseId/mainTasks/qualifications/question/answer."
            );
        }
    }

    private void validateDifferentFiles(Path inputPath, Path outputPath) {
        if (inputPath.toAbsolutePath().normalize().equals(outputPath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("NLG judge output must not overwrite input CSV.");
        }
    }

    private void validateComparisonInputs(List<Path> inputPaths) {
        if (inputPaths == null || inputPaths.isEmpty()) {
            throw new IllegalArgumentException("evaluation.nlg-judge.compare-inputs 값을 지정해야 합니다.");
        }
    }

    private void validateComparisonOutput(List<Path> inputPaths, Path outputPath) {
        for (Path inputPath : inputPaths) {
            validateDifferentFiles(inputPath, outputPath);
        }
    }

    private long validateCreatedOutputFile(
            Path outputPath,
            String description,
            int actualSummaryRows,
            int expectedSummaryRows
    ) throws IOException {
        if (!Files.exists(outputPath)) {
            throw new IllegalStateException(description + " was not created: " + outputPath);
        }
        if (!Files.isRegularFile(outputPath)) {
            throw new IllegalStateException(description + " is not a regular file: " + outputPath);
        }
        long sizeBytes = Files.size(outputPath);
        if (sizeBytes <= 0) {
            throw new IllegalStateException(description + " is empty: " + outputPath);
        }
        if (expectedSummaryRows <= 0 || actualSummaryRows <= 0 || actualSummaryRows != expectedSummaryRows) {
            throw new IllegalStateException(
                    description
                            + " row count mismatch: expected="
                            + expectedSummaryRows
                            + ", actual="
                            + actualSummaryRows
                            + ", path="
                            + outputPath
            );
        }
        return sizeBytes;
    }

    private NlgEvaluationComparisonResult buildComparisonResult(Path inputPath, List<Map<String, String>> rows) {
        List<Map<String, String>> successfulRows = rows.stream()
                .filter(row -> !StringUtils.hasText(value(row, "failureStage")))
                .toList();
        return new NlgEvaluationComparisonResult(
                inputPath.toString(),
                rows.size(),
                successfulRows.size(),
                averageColumn(successfulRows, "averageRelevance"),
                averageColumn(successfulRows, "averageProblemValidity"),
                averageColumn(successfulRows, "averageSentenceTypeConsistency"),
                averageColumn(successfulRows, "averageReasonCorrectness"),
                averageColumn(successfulRows, "averageContextAwareness"),
                averageColumn(successfulRows, "averageFaithfulness"),
                averageColumn(successfulRows, "averageTenseConsistency"),
                averageColumn(successfulRows, "averageUsability"),
                averageColumn(successfulRows, "averageNonMeta"),
                averageColumn(successfulRows, "averageMeaningPreservation"),
                averageColumn(successfulRows, "noAnalysisAppropriateness"),
                averageColumn(successfulRows, "strengthsPrecision"),
                averageColumn(successfulRows, "strengthsCoverage"),
                averageColumn(successfulRows, "missingKeywordsPrecision"),
                averageColumn(successfulRows, "missingKeywordsCoverage"),
                averageColumn(successfulRows, "overallUsefulness"),
                averageColumn(successfulRows, "judgeInputTokens"),
                averageColumn(successfulRows, "judgeOutputTokens"),
                averageColumn(successfulRows, "judgeLatencyMs"),
                averageColumn(successfulRows, "analysisCount"),
                errorCodeRate(successfulRows, NlgEvaluationErrorCode.META_IMPROVEMENT),
                errorCodeRate(successfulRows, NlgEvaluationErrorCode.UNSUPPORTED_FACT),
                errorCodeRate(successfulRows, NlgEvaluationErrorCode.FALSE_POSITIVE_ANALYSIS),
                fatalErrorRate(successfulRows),
                writeJson(errorCodeCounts(successfulRows))
        );
    }

    private Double averageColumn(List<Map<String, String>> rows, String column) {
        List<Double> values = rows.stream()
                .map(row -> parseDouble(value(row, column)))
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return Math.round(average * 100.0) / 100.0;
    }

    private Double errorCodeRate(List<Map<String, String>> rows, NlgEvaluationErrorCode code) {
        if (rows.isEmpty()) {
            return null;
        }
        long count = rows.stream()
                .filter(row -> readErrorCodeNames(value(row, "errorCodes")).contains(code.name()))
                .count();
        return Math.round((count * 100.0 / rows.size()) * 100.0) / 100.0;
    }

    private Double fatalErrorRate(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Set<String> fatalCodes = Set.of(
                NlgEvaluationErrorCode.UNSUPPORTED_FACT.name(),
                NlgEvaluationErrorCode.TENSE_CHANGED.name(),
                NlgEvaluationErrorCode.FALSE_POSITIVE_ANALYSIS.name(),
                NlgEvaluationErrorCode.INVALID_MISSING_KEYWORD.name()
        );
        long count = rows.stream()
                .filter(row -> readErrorCodeNames(value(row, "errorCodes")).stream().anyMatch(fatalCodes::contains))
                .count();
        return Math.round((count * 100.0 / rows.size()) * 100.0) / 100.0;
    }

    private Map<String, Long> errorCodeCounts(List<Map<String, String>> rows) {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (Map<String, String> row : rows) {
            for (String code : readErrorCodeNames(value(row, "errorCodes"))) {
                counts.merge(code, 1L, Long::sum);
            }
        }
        return counts;
    }

    private Set<String> readErrorCodeNames(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values.stream()
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
        } catch (JsonProcessingException e) {
            return Set.of();
        }
    }

    private Double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncateShortRationale(String rationale) {
        if (!StringUtils.hasText(rationale)) {
            return "";
        }
        String normalized = rationale.trim();
        if (normalized.length() <= MAX_SHORT_RATIONALE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SHORT_RATIONALE_LENGTH);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }

    private String requiredResolvedValue(
            Map<String, String> row,
            SourceCaseRows sourceCaseRows,
            ResolvedHeaders resolvedHeaders,
            String logicalField,
            String caseId
    ) {
        String value = resolvedValue(row, sourceCaseRows, resolvedHeaders, logicalField);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    "NLG judge input CSV has blank required field. caseId="
                            + caseId
                            + ", field="
                            + logicalField
                            + ", resolvedHeader="
                            + resolvedHeaders.describe(logicalField)
            );
        }
        return value;
    }

    private String resolvedValue(
            Map<String, String> row,
            SourceCaseRows sourceCaseRows,
            ResolvedHeaders resolvedHeaders,
            String logicalField
    ) {
        ResolvedHeader resolvedHeader = resolvedHeaders.get(logicalField);
        if (resolvedHeader == null) {
            return "";
        }
        if (resolvedHeader.location() == HeaderLocation.INPUT) {
            return value(row, resolvedHeader.headerName());
        }
        if (resolvedHeader.location() == HeaderLocation.SOURCE) {
            String caseId = value(row, resolvedHeaders.get("caseId").headerName());
            return value(sourceCaseRows.row(caseId), resolvedHeader.headerName());
        }
        if (resolvedHeader.location() == HeaderLocation.MISSING) {
            return "";
        }
        return valueFromJson(row, logicalField);
    }

    private String valueFromJson(Map<String, String> row, String logicalField) {
        for (String header : INPUT_JSON_HEADERS) {
            String json = value(row, header);
            if (!StringUtils.hasText(json)) {
                continue;
            }
            try {
                Map<String, Object> values = objectMapper.readValue(json, new TypeReference<>() {
                });
                Object value = values.get(logicalField);
                if (value instanceof String text && StringUtils.hasText(text)) {
                    return text;
                }
            } catch (JsonProcessingException ignored) {
                // Ignore non-object or malformed JSON fields and continue with other sources.
            }
        }
        return "";
    }

    private String failureStage(Exception e) {
        if (e instanceof JsonProcessingException || e instanceof IllegalArgumentException) {
            return "judge_validation_failed";
        }
        return "judge_call_failed";
    }

    record NlgEvaluationSummary(
            int totalCount,
            int successCount,
            int failureCount,
            Path outputPath
    ) {
    }

    record NlgEvaluationComparisonSummary(
            int fileCount,
            Path outputPath,
            int summaryRowCount,
            long sizeBytes
    ) {
    }

    private enum HeaderLocation {
        INPUT,
        SOURCE,
        JSON,
        MISSING
    }

    private record ResolvedHeader(HeaderLocation location, String headerName) {
        static ResolvedHeader input(String headerName) {
            return new ResolvedHeader(HeaderLocation.INPUT, headerName);
        }

        static ResolvedHeader source(String headerName) {
            return new ResolvedHeader(HeaderLocation.SOURCE, headerName);
        }

        static ResolvedHeader json(String logicalField) {
            return new ResolvedHeader(HeaderLocation.JSON, logicalField);
        }

        static ResolvedHeader missing(String logicalField) {
            return new ResolvedHeader(HeaderLocation.MISSING, logicalField);
        }

        String describe() {
            return switch (location) {
                case INPUT -> headerName;
                case SOURCE -> DEFAULT_SOURCE_CASES_FILE + ":" + headerName;
                case JSON -> "json:" + headerName;
                case MISSING -> "missing:" + headerName;
            };
        }
    }

    private record ResolvedHeaders(Map<String, ResolvedHeader> headers) {
        ResolvedHeader get(String logicalField) {
            return headers.get(logicalField);
        }

        boolean has(String logicalField, SourceCaseRows sourceCaseRows) {
            ResolvedHeader header = get(logicalField);
            if (header == null) {
                return false;
            }
            if (header.location() == HeaderLocation.SOURCE) {
                return sourceCaseRows.hasRows();
            }
            return header.location() == HeaderLocation.INPUT || header.location() == HeaderLocation.JSON;
        }

        String describe(String logicalField) {
            ResolvedHeader header = get(logicalField);
            return header == null ? "" : header.describe();
        }

        String logSummary() {
            return headers.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue().describe())
                    .collect(Collectors.joining(", "));
        }
    }

    private record SourceCaseRows(Path path, List<String> headers, Map<String, Map<String, String>> rowsByCaseId) {
        static SourceCaseRows empty() {
            return new SourceCaseRows(null, List.of(), Map.of());
        }

        boolean hasRows() {
            return !rowsByCaseId.isEmpty();
        }

        Map<String, String> row(String caseId) {
            if (!StringUtils.hasText(caseId)) {
                return Map.of();
            }
            return rowsByCaseId.getOrDefault(caseId, Map.of());
        }
    }
}
