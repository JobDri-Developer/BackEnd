package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationLlmSnapshot;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeyword;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSource;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationQuestionAnalysis;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EvaluationLlmSnapshotParser {
    private final ObjectMapper objectMapper;

    public EvaluationLlmSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvaluationLlmSnapshot parseRawLlmResponse(String rawLlmResponseJson) {
        if (!StringUtils.hasText(rawLlmResponseJson)) {
            return emptySnapshot();
        }
        try {
            JsonNode root = objectMapper.readTree(rawLlmResponseJson);
            return new EvaluationLlmSnapshot(
                    root.path("jobFit").isNumber() ? root.path("jobFit").intValue() : null,
                    root.path("impact").isNumber() ? root.path("impact").intValue() : null,
                    root.path("completeness").isNumber() ? root.path("completeness").intValue() : null,
                    root.path("feedback").asText(""),
                    readKeyStrengthQuotes(root.path("keyStrengths")),
                    readMissingKeywords(root.path("missingKeywords")),
                    readQuestionAnalyses(root.path("questionAnalyses"))
            );
        } catch (JsonProcessingException e) {
            return emptySnapshot();
        }
    }

    public List<EvaluationMissingKeyword> parseMissingKeywords(String json, String caseId) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalArgumentException("aiMissingKeywordsJson must be a JSON array. caseId=" + caseId);
            }
            return readMissingKeywords(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("aiMissingKeywordsJson must be a JSON array. caseId=" + caseId, e);
        }
    }

    private EvaluationLlmSnapshot emptySnapshot() {
        return new EvaluationLlmSnapshot(null, null, null, "", List.of(), List.of(), List.of());
    }

    private List<String> readKeyStrengthQuotes(JsonNode keyStrengthsNode) {
        if (!keyStrengthsNode.isArray()) {
            return List.of();
        }
        List<String> quotes = new ArrayList<>();
        for (JsonNode item : keyStrengthsNode) {
            String quote = item.path("quote").asText(null);
            if (StringUtils.hasText(quote)) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    private List<EvaluationMissingKeyword> readMissingKeywords(JsonNode missingKeywordsNode) {
        if (!missingKeywordsNode.isArray()) {
            return List.of();
        }
        List<EvaluationMissingKeyword> missingKeywords = new ArrayList<>();
        for (JsonNode item : missingKeywordsNode) {
            String keyword = item.path("keyword").asText(null);
            String source = item.path("source").asText(null);
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            EvaluationMissingKeywordSource.from(source)
                    .ifPresent(value -> missingKeywords.add(new EvaluationMissingKeyword(keyword, value)));
        }
        return missingKeywords;
    }

    private List<EvaluationQuestionAnalysis> readQuestionAnalyses(JsonNode questionAnalysesNode) {
        if (!questionAnalysesNode.isArray()) {
            return List.of();
        }
        List<EvaluationQuestionAnalysis> questionAnalyses = new ArrayList<>();
        for (JsonNode item : questionAnalysesNode) {
            questionAnalyses.add(new EvaluationQuestionAnalysis(
                    item.path("questionId").isNumber() ? item.path("questionId").longValue() : null,
                    item.path("sentence").asText(null),
                    item.path("status").asText(null),
                    item.path("reason").asText(null),
                    item.path("improvement").asText(null)
            ));
        }
        return questionAnalyses;
    }
}
