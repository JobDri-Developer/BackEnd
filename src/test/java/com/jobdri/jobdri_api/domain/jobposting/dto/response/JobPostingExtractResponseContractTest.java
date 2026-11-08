package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFinalizeRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerResultStoreRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingExtractResponseContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void workerPayloadPreservesExtractedFieldsThroughStorageRoundTrip() throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/job-posting-worker-finalize.json")) {
            assertThat(input).isNotNull();
            var payload = mapper.readTree(input);
            var request = mapper.treeToValue(payload, JobPostingWorkerFinalizeRequest.class);
            assertThat(request.extracted().requiredSkills()).containsExactly("Java", "SQL");
            assertThat(request.extracted().deadlineAt()).isEqualTo("2026-11-30T18:00:00");
            var stored = new JobPostingWorkerResultStoreRequest(1L, request);
            var restored = mapper.readValue(mapper.writeValueAsString(stored), JobPostingWorkerResultStoreRequest.class);
            assertThat(mapper.readTree(mapper.writeValueAsString(restored.result())).get("extracted"))
                    .isEqualTo(payload.get("extracted"));
        }
    }

    @Test
    void missingAndExplicitNullFieldsUseEmptyDefaults() throws Exception {
        for (String json : new String[]{"{}", "{\"requiredSkills\":null,\"deadlineAt\":null}"}) {
            var extracted = mapper.readValue(json, JobPostingExtractResponse.class);
            assertThat(extracted.requiredSkills()).isEmpty();
            assertThat(extracted.deadlineAt()).isEmpty();
            var serialized = mapper.valueToTree(extracted);
            assertThat(serialized.get("requiredSkills").isArray()).isTrue();
            assertThat(serialized.get("deadlineAt").asText()).isEmpty();
        }
    }
}
