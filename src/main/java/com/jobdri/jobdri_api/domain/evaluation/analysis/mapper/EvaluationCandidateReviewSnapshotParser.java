package com.jobdri.jobdri_api.domain.evaluation.analysis.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateReviewDecision;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationCandidateReviewSnapshot;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EvaluationCandidateReviewSnapshotParser {
    private final ObjectMapper objectMapper;

    public EvaluationCandidateReviewSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvaluationCandidateReviewSnapshot parse(String json) {
        if (!StringUtils.hasText(json)) {
            return emptySnapshot();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return new EvaluationCandidateReviewSnapshot(readDecisions(root.path("decisions")));
        } catch (JsonProcessingException e) {
            return emptySnapshot();
        }
    }

    private EvaluationCandidateReviewSnapshot emptySnapshot() {
        return new EvaluationCandidateReviewSnapshot(List.of());
    }

    private List<EvaluationCandidateReviewDecision> readDecisions(JsonNode decisionsNode) {
        if (!decisionsNode.isArray()) {
            return List.of();
        }
        List<EvaluationCandidateReviewDecision> decisions = new ArrayList<>();
        for (JsonNode item : decisionsNode) {
            decisions.add(new EvaluationCandidateReviewDecision(
                    item.path("accepted").isBoolean() ? item.path("accepted").booleanValue() : null,
                    item.path("rejectionCode").asText(null)
            ));
        }
        return decisions;
    }
}
