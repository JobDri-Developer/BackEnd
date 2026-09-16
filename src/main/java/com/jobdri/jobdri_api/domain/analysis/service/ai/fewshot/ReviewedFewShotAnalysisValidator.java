package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Validates the partial analysis contract used by reviewed prompt examples.
 * Scores and feedback are deliberately not required; semantic approval remains a PM task.
 */
final class ReviewedFewShotAnalysisValidator {
    private static final Set<String> STATUSES = Set.of("proven", "mentioned", "fabricated");
    private static final Set<String> SOURCES = Set.of("mainTask", "qualification", "preference");

    private ReviewedFewShotAnalysisValidator() {
    }

    static boolean isValid(JsonNode analysis) {
        if (analysis == null || !analysis.isObject()
                || !analysis.path("keyStrengths").isArray()
                || !analysis.path("missingKeywords").isArray()
                || !analysis.path("questionAnalyses").isArray()) {
            return false;
        }
        for (JsonNode strength : analysis.path("keyStrengths")) {
            if (!text(strength, "title") || !text(strength, "quote")) {
                return false;
            }
        }
        for (JsonNode missing : analysis.path("missingKeywords")) {
            if (!text(missing, "keyword") || !text(missing, "source")
                    || !SOURCES.contains(missing.path("source").textValue())) {
                return false;
            }
        }
        for (JsonNode sentence : analysis.path("questionAnalyses")) {
            JsonNode questionId = sentence.path("questionId");
            if (!questionId.isIntegralNumber() || !questionId.canConvertToLong() || questionId.longValue() <= 0
                    || !text(sentence, "sentence") || !text(sentence, "reason")
                    || !text(sentence, "status") || !STATUSES.contains(sentence.path("status").textValue())
                    || !sentence.has("improvement")
                    || !(sentence.path("improvement").isNull() || text(sentence, "improvement"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean text(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).textValue().isBlank();
    }
}
