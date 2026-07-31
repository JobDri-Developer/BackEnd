package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.service.ai.FewShotPromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class FewShotCaseStore {
    private final FewShotPromptProvider promptProvider;
    private final FewShotProperties properties;
    private final ObjectMapper objectMapper;

    public FewShotCaseStore(
            FewShotPromptProvider promptProvider,
            FewShotProperties properties,
            ObjectMapper objectMapper
    ) {
        this.promptProvider = promptProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<FewShotCase> loadActiveCases() {
        List<FewShotCase> cases = new ArrayList<>();
        if (properties.getSource().isFixedEnabled()) {
            cases.addAll(loadFixedCases());
        }
        if (properties.getSource().isCuratedEnabled()) {
            cases.addAll(loadJsonCases(properties.getCuratedResource(), FewShotSource.CURATED));
        }
        if (properties.getSource().isReviewedEvaluationEnabled()) {
            cases.addAll(loadJsonCases(properties.getReviewedEvaluationResource(), FewShotSource.REVIEWED_EVALUATION));
            cases.addAll(loadReviewedEvaluationCsvCases(properties.getReviewedEvaluationCsvPath()));
        }
        List<FewShotCase> validCases = filterSearchable(cases);
        log.debug(
                "few-shot cases loaded. total={}, active={}, datasetVersion={}",
                cases.size(),
                validCases.size(),
                properties.getDatasetVersion()
        );
        return validCases;
    }

    private List<FewShotCase> loadFixedCases() {
        List<String> blocks = promptProvider.getFixedExampleBlocks();
        List<FewShotCase> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            String block = blocks.get(i);
            result.add(new FewShotCase(
                    "FS-FIXED-" + (i + 1),
                    FewShotSource.FIXED,
                    FewShotReviewStatus.APPROVED,
                    true,
                    0,
                    "",
                    "",
                    List.of(),
                    List.of(),
                    extractSectionLine(block, "- questionId:"),
                    extractSectionLine(block, "- answer:"),
                    "{}",
                    List.of("fixed"),
                    properties.getDatasetVersion(),
                    block
            ));
        }
        return result;
    }

    private List<FewShotCase> loadJsonCases(String resourcePath, FewShotSource expectedSource) {
        if (!StringUtils.hasText(resourcePath)) {
            return List.of();
        }
        ClassPathResource resource = new ClassPathResource(stripClasspathPrefix(resourcePath));
        if (!resource.exists()) {
            log.debug("few-shot resource not found. source={}, resource={}", expectedSource, resourcePath);
            return List.of();
        }
        try (var inputStream = resource.getInputStream()) {
            String json = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return List.of();
            }
            List<FewShotCase> cases = objectMapper.readValue(json, new TypeReference<>() {
            });
            return cases.stream()
                    .map(fewShotCase -> normalizeSource(fewShotCase, expectedSource))
                    .toList();
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "few-shot resource parse failed. source={}, resource={}, message={}",
                    expectedSource,
                    resourcePath,
                    e.getMessage()
            );
            return List.of();
        }
    }

    private List<FewShotCase> filterSearchable(List<FewShotCase> cases) {
        List<FewShotCase> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (FewShotCase fewShotCase : cases) {
            if (fewShotCase == null || !StringUtils.hasText(fewShotCase.id())) {
                continue;
            }
            if (!ids.add(fewShotCase.id())) {
                log.warn("few-shot case skipped. reason=duplicate_case_id, id={}, source={}", fewShotCase.id(), fewShotCase.source());
                continue;
            }
            if (!fewShotCase.searchable()) {
                log.debug("few-shot case skipped. reason=not_searchable, id={}, source={}", fewShotCase.id(), fewShotCase.source());
                continue;
            }
            if (!StringUtils.hasText(fewShotCase.promptBlock())) {
                log.debug("few-shot case skipped. reason=blank_prompt_block, id={}, source={}", fewShotCase.id(), fewShotCase.source());
                continue;
            }
            result.add(fewShotCase);
        }
        return List.copyOf(result);
    }

    private List<FewShotCase> loadReviewedEvaluationCsvCases(String csvPath) {
        if (!StringUtils.hasText(csvPath)) {
            return List.of();
        }
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            log.warn("reviewed evaluation few-shot CSV not found. path={}", csvPath);
            return List.of();
        }
        try {
            List<Map<String, String>> rows = readCsv(path);
            List<FewShotCase> result = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (Map<String, String> row : rows) {
                String id = value(row, "caseId");
                if (!ids.add(id)) {
                    log.warn("reviewed evaluation few-shot row skipped. reason=duplicate_case_id, caseId={}", id);
                    continue;
                }
                if (!"true".equalsIgnoreCase(value(row, "fewShotEnabled"))) {
                    continue;
                }
                if (!"APPROVED".equalsIgnoreCase(value(row, "reviewStatus"))) {
                    log.debug("reviewed evaluation few-shot row skipped. reason=not_approved, caseId={}", id);
                    continue;
                }
                String sanitizedAnswer = value(row, "sanitizedAnswer");
                String approvedAnalysisJson = value(row, "approvedAnalysisJson");
                if (!StringUtils.hasText(id)
                        || !StringUtils.hasText(value(row, "mainTasks"))
                        || !StringUtils.hasText(value(row, "qualifications"))
                        || !StringUtils.hasText(value(row, "question"))
                        || !StringUtils.hasText(sanitizedAnswer)
                        || !StringUtils.hasText(approvedAnalysisJson)) {
                    log.warn("reviewed evaluation few-shot row skipped. reason=missing_required_field, caseId={}", id);
                    continue;
                }
                result.add(new FewShotCase(
                        id,
                        FewShotSource.REVIEWED_EVALUATION,
                        FewShotReviewStatus.APPROVED,
                        true,
                        parseInt(value(row, "fewShotPriority")),
                        value(row, "jobCategorySmall"),
                        value(row, "jobCategorySmall"),
                        splitLines(value(row, "mainTasks")),
                        splitLines(value(row, "qualifications")),
                        value(row, "question"),
                        sanitizedAnswer,
                        approvedAnalysisJson,
                        splitTags(value(row, "fewShotTags")),
                        properties.getDatasetVersion(),
                        buildReviewedPromptBlock(id, row, sanitizedAnswer, approvedAnalysisJson)
                ));
            }
            log.info("reviewed evaluation few-shot CSV loaded. rows={}, accepted={}, path={}", rows.size(), result.size(), csvPath);
            return result;
        } catch (IOException | RuntimeException e) {
            log.warn("reviewed evaluation few-shot CSV parse failed. path={}, message={}", csvPath, e.getMessage());
            return List.of();
        }
    }

    private FewShotCase normalizeSource(FewShotCase fewShotCase, FewShotSource expectedSource) {
        return new FewShotCase(
                fewShotCase.id(),
                fewShotCase.source() == null ? expectedSource : fewShotCase.source(),
                fewShotCase.reviewStatus(),
                fewShotCase.enabled(),
                fewShotCase.priority(),
                fewShotCase.jobCategory(),
                fewShotCase.jobTitle(),
                fewShotCase.mainTasks(),
                fewShotCase.qualifications(),
                fewShotCase.question(),
                fewShotCase.sanitizedAnswer(),
                fewShotCase.approvedAnalysisJson(),
                fewShotCase.tags(),
                StringUtils.hasText(fewShotCase.datasetVersion()) ? fewShotCase.datasetVersion() : properties.getDatasetVersion(),
                fewShotCase.promptBlock()
        );
    }

    private static String stripClasspathPrefix(String resourcePath) {
        return resourcePath.startsWith("classpath:") ? resourcePath.substring("classpath:".length()) : resourcePath;
    }

    private static String extractSectionLine(String block, String prefix) {
        if (!StringUtils.hasText(block)) {
            return "";
        }
        return block.lines()
                .map(String::trim)
                .filter(line -> line.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse("");
    }

    private static String buildReviewedPromptBlock(
            String id,
            Map<String, String> row,
            String sanitizedAnswer,
            String approvedAnalysisJson
    ) {
        return """
                ## 예시 %s: 검수 승인 평가 사례
                관련 JD 요구사항:
                - mainTask: %s
                - qualification: %s

                평가 대상 답변:
                - questionId: 1
                - question: %s
                - answer: %s

                출력 중 강점/문장/누락 관련 필드:
                %s
                """.formatted(
                id,
                value(row, "mainTasks"),
                value(row, "qualifications"),
                value(row, "question"),
                sanitizedAnswer,
                approvedAnalysisJson
        ).trim();
    }

    private static List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private static int parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String value(Map<String, String> row, String key) {
        return row == null ? "" : row.getOrDefault(key, "").trim();
    }

    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        List<List<String>> rows = parseRows(content);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(rows.getFirst());
        if (!headers.isEmpty() && !headers.getFirst().isEmpty() && headers.getFirst().charAt(0) == '\uFEFF') {
            headers.set(0, headers.getFirst().substring(1));
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                values.put(headers.get(i), i < row.size() ? row.get(i) : "");
            }
            result.add(values);
        }
        return result;
    }

    private static List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (inQuotes) {
                if (current == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == '"') {
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
        row.add(field.toString());
        if (!row.isEmpty() && row.stream().anyMatch(value -> !value.isBlank())) {
            rows.add(row);
        }
        return rows;
    }
}
