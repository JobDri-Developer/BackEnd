package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisWorkerContextResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("worker context JSON에 유사 공고 context를 포함하고 embedding과 소유자 정보는 노출하지 않는다")
    void serializeSimilarJobPostings() throws Exception {
        SimilarJobPostingContext similarJobPosting = new SimilarJobPostingContext(
                31L,
                "유사 회사",
                "유사 공고",
                "서버 개발자",
                "API 개발",
                "Java",
                "AWS",
                1,
                0.91
        );
        AnalysisWorkerContextResponse response = new AnalysisWorkerContextResponse(
                1L,
                10L,
                "현재 회사",
                "백엔드 개발자",
                "현재 업무",
                "현재 자격",
                "현재 우대",
                "개발",
                "서버",
                "백엔드",
                List.of(),
                List.of(similarJobPosting)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("similarJobPostings").size()).isEqualTo(1);
        assertThat(json.path("similarJobPostings").get(0).path("jobPostingId").asLong()).isEqualTo(31L);
        assertThat(json.path("similarJobPostings").get(0).path("similarityRank").asInt()).isEqualTo(1);
        assertThat(json.path("similarJobPostings").get(0).has("embedding")).isFalse();
        assertThat(json.path("similarJobPostings").get(0).has("userId")).isFalse();
    }

    @Test
    @DisplayName("기존 생성자는 유사 공고를 빈 배열로 직렬화한다")
    void oldConstructorDefaultsSimilarJobPostingsToEmptyList() throws Exception {
        AnalysisWorkerContextResponse response = new AnalysisWorkerContextResponse(
                1L,
                10L,
                "현재 회사",
                "백엔드 개발자",
                "현재 업무",
                "현재 자격",
                "현재 우대",
                "개발",
                "서버",
                "백엔드",
                List.of()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("similarJobPostings").isArray()).isTrue();
        assertThat(json.path("similarJobPostings").size()).isZero();
    }
}
