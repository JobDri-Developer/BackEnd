package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.cohere.CohereProperties;
import com.pgvector.PGvector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingEmbeddingSyncServiceTest {

    private final JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
    private final JobPostingEmbeddingService embeddingService = mock(JobPostingEmbeddingService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final JobPostingEmbeddingSyncService syncService = new JobPostingEmbeddingSyncService(
            jobPostingRepository,
            embeddingService,
            new CohereProperties(
                    "test-api-key",
                    "https://api.cohere.com",
                    new CohereProperties.Embedding(
                            "embed-v4.0",
                            1024,
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(2)
                    )
            ),
            dataSource
    );

    @Test
    @DisplayName("syncJobPosting은 단일 채용 공고 embedding을 생성해 PGVector upsert로 저장한다")
    void syncJobPosting() throws Exception {
        JobPosting jobPosting = jobPosting(10L, "백엔드 개발자");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(embeddingService.embedAll(List.of(jobPosting))).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        try (MockedStatic<PGvector> pgvector = mockStatic(PGvector.class)) {
            int processed = syncService.syncJobPosting(10L);

            assertThat(processed).isEqualTo(1);
            pgvector.verify(() -> PGvector.registerTypes(connection));
            verify(connection).prepareStatement(org.mockito.ArgumentMatchers.contains("job_posting_embeddings"));
            verify(statement).setLong(1, 10L);
            verify(statement).setString(2, "embed-v4.0");
            verify(statement).setObject(eq(3), isA(PGvector.class));
            verify(statement).setTimestamp(eq(4), any());
            verify(statement).setTimestamp(eq(5), any());
            verify(statement).addBatch();
            verify(statement).executeBatch();
        }
    }

    @Test
    @DisplayName("syncAllJobPostings는 id 오름차순 전체 공고를 batch size대로 저장한다")
    void syncAllJobPostings() throws Exception {
        ReflectionTestUtils.setField(syncService, "batchSize", 1);
        JobPosting first = jobPosting(1L, "백엔드 개발자");
        JobPosting second = jobPosting(2L, "데이터 엔지니어");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(jobPostingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));
        when(embeddingService.embedAll(List.of(first))).thenReturn(List.of(new float[]{0.1f}));
        when(embeddingService.embedAll(List.of(second))).thenReturn(List.of(new float[]{0.2f}));
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        try (MockedStatic<PGvector> ignored = mockStatic(PGvector.class)) {
            int processed = syncService.syncAllJobPostings();

            assertThat(processed).isEqualTo(2);
            verify(embeddingService).embedAll(List.of(first));
            verify(embeddingService).embedAll(List.of(second));
            verify(statement).setLong(1, 1L);
            verify(statement).setLong(1, 2L);
            verify(statement, times(2)).executeBatch();
        }
    }

    @Test
    @DisplayName("임베딩 결과 개수가 채용 공고 개수와 다르면 저장하지 않는다")
    void mismatchedEmbeddingCount() {
        JobPosting jobPosting = jobPosting(10L, "백엔드 개발자");
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(embeddingService.embedAll(List.of(jobPosting))).thenReturn(List.of());

        assertThatThrownBy(() -> syncService.syncJobPosting(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("채용 공고 개수");
    }

    @Test
    @DisplayName("존재하지 않는 채용 공고는 명확한 예외를 발생시킨다")
    void jobPostingNotFound() {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> syncService.syncJobPosting(999L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.JOB_POSTING_NOT_FOUND));
    }

    private JobPosting jobPosting(Long id, String jobTitle) {
        Classification classification = Classification.create("개발");
        DetailClassification detailClassification = classification
                .addMiddleClassification("서버")
                .addDetailClassification("백엔드");
        JobPosting jobPosting = JobPosting.create(
                User.authenticatedPrincipal(1L, "user@example.com", UserRole.USER),
                Company.create("테스트 기업", CompanySize.MEDIUM),
                detailClassification,
                JobPostingProfileColor.DEFAULT,
                "테스트 공고",
                jobTitle,
                "Spring Boot API 개발",
                "Java, Spring, JPA",
                "AWS, Docker"
        );
        ReflectionTestUtils.setField(jobPosting, "id", id);
        return jobPosting;
    }
}
