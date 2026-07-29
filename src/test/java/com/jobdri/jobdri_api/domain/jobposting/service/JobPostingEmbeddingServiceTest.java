package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingEmbeddingServiceTest {

    private final JobPostingEmbeddingTextBuilder textBuilder = new JobPostingEmbeddingTextBuilder();
    private final CohereEmbeddingClient cohereEmbeddingClient = mock(CohereEmbeddingClient.class);
    private final JobPostingEmbeddingService embeddingService =
            new JobPostingEmbeddingService(textBuilder, cohereEmbeddingClient);

    @Test
    @DisplayName("채용 공고 embedding text를 Cohere search_document 요청으로 임베딩한다")
    void embedAll() {
        JobPosting first = jobPosting("백엔드 개발자", "Spring Boot API 개발", "Java", "AWS");
        JobPosting second = jobPosting("데이터 엔지니어", "ETL 파이프라인 개발", "Python", "Airflow");
        List<float[]> expected = List.of(new float[]{0.1f, 0.2f}, new float[]{0.3f, 0.4f});
        when(cohereEmbeddingClient.embedDocuments(List.of(
                textBuilder.build(first),
                textBuilder.build(second)
        ))).thenReturn(expected);

        List<float[]> result = embeddingService.embedAll(List.of(first, second));

        assertThat(result).isSameAs(expected);
        verify(cohereEmbeddingClient).embedDocuments(List.of(textBuilder.build(first), textBuilder.build(second)));
    }

    private JobPosting jobPosting(String jobTitle, String task, String requirement, String preferred) {
        Classification classification = Classification.create("개발");
        DetailClassification detailClassification = classification
                .addMiddleClassification("서버")
                .addDetailClassification("백엔드");
        return JobPosting.create(
                User.authenticatedPrincipal(1L, "user@example.com", UserRole.USER),
                Company.create("테스트 기업", CompanySize.MEDIUM),
                detailClassification,
                JobPostingProfileColor.DEFAULT,
                "테스트 공고",
                jobTitle,
                task,
                requirement,
                preferred
        );
    }
}
