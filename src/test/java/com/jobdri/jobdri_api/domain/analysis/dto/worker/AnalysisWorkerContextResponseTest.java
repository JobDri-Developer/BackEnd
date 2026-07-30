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
                List.of(new CorpusReferenceContext(
                        11L,
                        "JOB_POSTING",
                        "참고 회사 - 백엔드",
                        "주요 업무: API 개발",
                        1
                )),
                List.of(similarJobPosting)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("similarJobPostings").size()).isEqualTo(1);
        assertThat(json.path("similarJobPostings").get(0).path("jobPostingId").asLong()).isEqualTo(31L);
        assertThat(json.path("similarJobPostings").get(0).path("similarityRank").asInt()).isEqualTo(1);
        assertThat(json.path("similarJobPostings").get(0).has("embedding")).isFalse();
        assertThat(json.path("similarJobPostings").get(0).has("userId")).isFalse();
        assertThat(json.path("corpusReferences").size()).isEqualTo(1);
        assertThat(json.path("corpusReferences").get(0).path("corpusId").asLong()).isEqualTo(11L);
        assertThat(json.path("corpusReferences").get(0).path("category").asText()).isEqualTo("JOB_POSTING");
        assertThat(json.path("corpusReferences").get(0).path("title").asText()).isEqualTo("참고 회사 - 백엔드");
        assertThat(json.path("corpusReferences").get(0).path("content").asText()).isEqualTo("주요 업무: API 개발");
        assertThat(json.path("corpusReferences").get(0).path("rank").asInt()).isEqualTo(1);
        assertThat(json.path("corpusReferences").get(0).has("distance")).isFalse();
        assertThat(json.path("corpusReferences").get(0).has("embedding")).isFalse();
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
        assertThat(json.path("corpusReferences").isArray()).isTrue();
        assertThat(json.path("corpusReferences").size()).isZero();
    }

    @Test
    @DisplayName("기존 worker snapshot JSON은 corpusReferences 없이도 역직렬화된다")
    void deserializeLegacySnapshotWithoutCorpusReferences() throws Exception {
        String legacySnapshot = """
                {
                  "userId": 1,
                  "mockApplyId": 10,
                  "companyName": "현재 회사",
                  "jobTitle": "백엔드 개발자",
                  "task": "API 개발",
                  "requirements": "Java",
                  "preferredQualifications": "AWS",
                  "bigClassificationName": "개발",
                  "middleClassificationName": "서버",
                  "detailClassificationName": "백엔드",
                  "questions": [],
                  "similarJobPostings": [{
                    "jobPostingId": 31,
                    "companyName": "유사 회사",
                    "postingName": "유사 공고",
                    "jobTitle": "서버 개발자",
                    "task": "API 개발",
                    "requirements": "Java",
                    "preferredQualifications": "AWS",
                    "similarityRank": 1,
                    "similarityScore": 0.91
                  }]
                }
                """;

        AnalysisWorkerContextResponse response = objectMapper.readValue(
                legacySnapshot,
                AnalysisWorkerContextResponse.class
        );

        assertThat(response.corpusReferences()).isEmpty();
        assertThat(response.similarJobPostings()).hasSize(1);
        assertThat(response.similarJobPostings().getFirst().jobPostingId()).isEqualTo(31L);
    }
}
