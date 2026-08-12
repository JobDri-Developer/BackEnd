package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateSnapshot;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordCandidate;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSource;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EvaluationCandidateSnapshotParser {
    private final ObjectMapper objectMapper;

    public EvaluationCandidateSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvaluationCandidateSnapshot parse(String json, String fieldName, String caseId) {
        if (!StringUtils.hasText(json)) {
            return emptySnapshot();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return new EvaluationCandidateSnapshot(
                    readOpaqueItems(root.path("strengthCandidates")),
                    readOpaqueItems(root.path("analysisCandidates")),
                    readMissingKeywordCandidates(root.path("missingKeywordCandidates"))
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid candidate JSON. caseId=" + caseId, e);
        }
    }

    private EvaluationCandidateSnapshot emptySnapshot() {
        return new EvaluationCandidateSnapshot(List.of(), List.of(), List.of());
    }

    private List<Object> readOpaqueItems(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<Object> items = new ArrayList<>();
        node.forEach(item -> items.add(item));
        return items;
    }

    private List<EvaluationMissingKeywordCandidate> readMissingKeywordCandidates(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<EvaluationMissingKeywordCandidate> candidates = new ArrayList<>();
        for (JsonNode item : node) {
            String keyword = item.path("keyword").asText(null);
            String source = item.path("source").asText(null);
            String relatedRequirement = item.path("relatedRequirement").asText(null);
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            EvaluationMissingKeywordSource.from(source)
                    .ifPresent(value -> candidates.add(new EvaluationMissingKeywordCandidate(
                            keyword,
                            value,
                            relatedRequirement
                    )));
        }
        return candidates;
    }
}
