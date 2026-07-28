package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JobCategoryEvaluationCriteriaProviderTest {

    private JobCategoryEvaluationCriteriaProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JobCategoryEvaluationCriteriaProvider(new ObjectMapper());
    }

    @Test
    @DisplayName("직무 중분류 평가 기준 JSON resource를 로드하고 중분류명으로 조회한다")
    void findByMiddleName() {
        Optional<JobCategoryEvaluationCriteria> criteria = provider.findByMiddleName("AI·개발·데이터");

        assertThat(criteria).isPresent();
        assertThat(criteria.get().jobCategoryMiddle()).isEqualTo("AI·개발·데이터");
        assertThat(criteria.get().coreCompetencies()).isNotEmpty();
        assertThat(criteria.get().missingKeywordExamples()).isNotEmpty();
    }

    @Test
    @DisplayName("중분류명 매칭 시 앞뒤 공백과 구분자 주변 공백을 정규화한다")
    void findByMiddleNameNormalizesWhitespaceAndSeparator() {
        Optional<JobCategoryEvaluationCriteria> criteria = provider.findByMiddleName(" AI / 개발 / 데이터 ");

        assertThat(criteria).isPresent();
        assertThat(criteria.get().jobCategoryMiddle()).isEqualTo("AI·개발·데이터");
    }

    @Test
    @DisplayName("없는 중분류명은 Optional.empty를 반환한다")
    void findByMiddleNameReturnsEmptyWhenMissing() {
        assertThat(provider.findByMiddleName("없는 중분류")).isEmpty();
        assertThat(provider.findByMiddleName(" ")).isEmpty();
    }
}
