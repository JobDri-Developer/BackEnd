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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class NlgEvaluationBatchService {
    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;
    private static final int MAX_SHORT_RATIONALE_LENGTH = 300;
    private static final List<String> REQUIRED_HEADERS = List.of(
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
        validateHeaders(EvaluationCsvSupport.readHeaders(inputPath));

        List<Map<String, String>> rows = EvaluationCsvSupport.read(inputPath);
        List<NlgEvaluationResult> results = new ArrayList<>();
        int successCount = 0;

        for (Map<String, String> row : rows) {
            String caseId = value(row, "caseId");
            try {
                NlgEvaluationAiClient.NlgJudgeInput input = buildJudgeInput(inputPath, row);
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
        validateComparisonOutput(inputPaths, outputPath);

        List<NlgEvaluationComparisonResult> results = new ArrayList<>();
        for (Path inputPath : inputPaths) {
            List<Map<String, String>> rows = EvaluationCsvSupport.read(inputPath);
            results.add(buildComparisonResult(inputPath, rows));
        }

        NlgEvaluationComparisonCsvSupport.write(outputPath, results);
        return new NlgEvaluationComparisonSummary(inputPaths.size(), outputPath);
    }

    private NlgEvaluationAiClient.NlgJudgeInput buildJudgeInput(Path inputPath, Map<String, String> row) {
        List<EvaluationQuestionAnalysisResult> questionAnalyses = readQuestionAnalyses(value(row, "aiQuestionAnalysesJson"));
        return new NlgEvaluationAiClient.NlgJudgeInput(
                value(row, "caseId"),
                inputPath.toString(),
                value(row, "mainTasks"),
                value(row, "qualifications"),
                value(row, "preferences"),
                value(row, "question"),
                value(row, "answer"),
                value(row, "aiQuestionAnalysesJson"),
                readKeyStrengthsJson(value(row, "rawLlmResponseJson")),
                value(row, "aiMissingKeywordsJson"),
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

        List<NlgEvaluationResponse.QuestionAnalysisEvaluation> evaluations =
                validQuestionEvaluations(input.questionAnalyses(), response.questionAnalysisEvaluations());
        if (input.questionAnalyses().isEmpty() && !evaluations.isEmpty()) {
            return NlgEvaluationResult.failed(input.caseId(), input.sourceResultFile(), "judge_validation_failed");
        }

        List<NlgEvaluationErrorCode> errorCodes = mergeErrorCodes(response.caseErrorCodes(), evaluations);
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
                validCaseScore(response.strengthsPrecision()),
                validCaseScore(response.missingKeywordsPrecision()),
                validCaseScore(response.overallUsefulness()),
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
                    sanitizeErrorCodes(evaluation.errorCodes())
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
            List<NlgEvaluationErrorCode> caseErrorCodes,
            List<NlgEvaluationResponse.QuestionAnalysisEvaluation> evaluations
    ) {
        Set<NlgEvaluationErrorCode> codes = new HashSet<>(sanitizeErrorCodes(caseErrorCodes));
        for (NlgEvaluationResponse.QuestionAnalysisEvaluation evaluation : evaluations) {
            codes.addAll(sanitizeErrorCodes(evaluation.errorCodes()));
        }
        if (codes.size() > 1) {
            codes.remove(NlgEvaluationErrorCode.NONE);
        }
        if (codes.isEmpty()) {
            return List.of(NlgEvaluationErrorCode.NONE);
        }
        return codes.stream()
                .sorted()
                .toList();
    }

    private List<NlgEvaluationErrorCode> sanitizeErrorCodes(List<NlgEvaluationErrorCode> errorCodes) {
        if (errorCodes == null || errorCodes.isEmpty()) {
            return List.of();
        }
        return errorCodes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

    private boolean validScore(Integer score) {
        return score != null && score >= MIN_SCORE && score <= MAX_SCORE;
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

    private void validateHeaders(List<String> headers) {
        Set<String> headerSet = new HashSet<>(headers);
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !headerSet.contains(header))
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException("NLG judge input CSV missing required headers: " + missingHeaders);
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
                averageColumn(successfulRows, "strengthsPrecision"),
                averageColumn(successfulRows, "missingKeywordsPrecision"),
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
            Path outputPath
    ) {
    }
}
