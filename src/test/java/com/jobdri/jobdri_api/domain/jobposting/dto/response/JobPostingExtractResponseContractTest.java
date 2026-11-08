package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerFinalizeRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.worker.JobPostingWorkerResultStoreRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Python worker 응답과 Java 역직렬화·재직렬화 사이의 추출 계약을 검증한다. */
class JobPostingExtractResponseContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** 저장용 envelope를 왕복해도 공통 샘플의 추출 필드가 보존되는지 검증한다. */
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

    /** 생략 및 명시적 null 필드가 빈 배열·빈 문자열로 직렬화되는지 검증한다. */
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
