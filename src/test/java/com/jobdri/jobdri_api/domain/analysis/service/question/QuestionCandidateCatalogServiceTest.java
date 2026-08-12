package com.jobdri.jobdri_api.domain.analysis.service.question;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionCandidateCatalogServiceTest {

    private final QuestionCandidateCatalogService questionCandidateCatalogService =
            new QuestionCandidateCatalogService();

    @Test
    @DisplayName("기본 후보와 custom 후보는 서로 다른 prefix의 candidateKey를 사용한다")
    void candidateKeysUseDistinctPrefixes() {
        var defaultCandidates = questionCandidateCatalogService.getDefaultCandidateResponses(Set.of());

        assertThat(defaultCandidates).isNotEmpty();
        assertThat(defaultCandidates.getFirst().candidateKey()).isEqualTo("default:1");
        assertThat(questionCandidateCatalogService.toCustomCandidateKey(1L)).isEqualTo("custom:1");
        assertThat(defaultCandidates.getFirst().candidateKey())
                .isNotEqualTo(questionCandidateCatalogService.toCustomCandidateKey(1L));
    }
}
