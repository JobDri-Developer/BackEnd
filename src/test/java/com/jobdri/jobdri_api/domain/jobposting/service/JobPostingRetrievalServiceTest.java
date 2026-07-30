package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingSimilarityResult;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.entity.UserRole;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import com.pgvector.PGvector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingRetrievalServiceTest {

    private final JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
    private final JobPostingEmbeddingTextBuilder textBuilder = new JobPostingEmbeddingTextBuilder();
    private final CohereEmbeddingClient cohereEmbeddingClient = mock(CohereEmbeddingClient.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final JobPostingRetrievalService retrievalService = new JobPostingRetrievalService(
            jobPostingRepository,
            textBuilder,
            cohereEmbeddingClient,
            dataSource
    );

    @Test
    @DisplayName("현재 공고 embedding query로 유사 JobPosting Top3를 조회한다")
    void findSimilarJobPostingsDefaultTop3() throws Exception {
        JobPosting current = jobPosting(10L, 7L, "백엔드 개발자");
        float[] queryVector = new float[]{0.1f, 0.2f};
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(current));
        when(cohereEmbeddingClient.embedQuery(textBuilder.build(current))).thenReturn(queryVector);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getLong("id")).thenReturn(11L, 12L);
        when(resultSet.getString("posting_name")).thenReturn("첫 번째 공고", "두 번째 공고");
        when(resultSet.getString("company_name")).thenReturn("첫 번째 회사", "두 번째 회사");
        when(resultSet.getString("job_title")).thenReturn("백엔드 엔지니어", "서버 개발자");
        when(resultSet.getDouble("distance")).thenReturn(0.12, 0.34);

        try (MockedStatic<PGvector> pgvector = mockStatic(PGvector.class)) {
            List<JobPostingSimilarityResult> results = retrievalService.findSimilarJobPostings(10L);

            assertThat(results).containsExactly(
                    new JobPostingSimilarityResult(11L, "첫 번째 공고", "첫 번째 회사", "백엔드 엔지니어", 0.88),
                    new JobPostingSimilarityResult(12L, "두 번째 공고", "두 번째 회사", "서버 개발자", 0.6599999999999999)
            );
            verify(cohereEmbeddingClient).embedQuery(textBuilder.build(current));
            pgvector.verify(() -> PGvector.registerTypes(connection));
            verify(statement).setObject(eq(1), isA(PGvector.class));
            verify(statement).setLong(2, 10L);
            verify(statement).setLong(3, 7L);
            verify(statement).setObject(eq(4), isA(PGvector.class));
            verify(statement).setInt(5, 3);
        }
    }

    @Test
    @DisplayName("limit 인자를 SQL LIMIT에 바인딩하고 현재 공고를 제외한다")
    void bindLimitAndExcludeCurrentJobPosting() throws Exception {
        JobPosting current = jobPosting(20L, 8L, "데이터 엔지니어");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(jobPostingRepository.findById(20L)).thenReturn(Optional.of(current));
        when(cohereEmbeddingClient.embedQuery(textBuilder.build(current))).thenReturn(new float[]{0.3f});
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<PGvector> ignored = mockStatic(PGvector.class)) {
            List<JobPostingSimilarityResult> results = retrievalService.findSimilarJobPostings(20L, 5);

            assertThat(results).isEmpty();
            verify(connection).prepareStatement(sqlCaptor.capture());
            assertThat(sqlCaptor.getValue())
                    .contains("FROM job_posting_embeddings e")
                    .contains("JOIN job_postings jp ON e.job_posting_id = jp.id")
                    .contains("jp.id <> ?")
                    .contains("jp.user_id = ?")
                    .contains("ORDER BY e.embedding <=> ?")
                    .contains("LIMIT ?");
            verify(statement).setLong(2, 20L);
            verify(statement).setLong(3, 8L);
            verify(statement).setInt(5, 5);
        }
    }

    @Test
    @DisplayName("limit가 1보다 작으면 최소 1로 보정한다")
    void minimumLimit() throws Exception {
        JobPosting current = jobPosting(30L, 9L, "프론트엔드 개발자");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(jobPostingRepository.findById(30L)).thenReturn(Optional.of(current));
        when(cohereEmbeddingClient.embedQuery(textBuilder.build(current))).thenReturn(new float[]{0.4f});
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        try (MockedStatic<PGvector> ignored = mockStatic(PGvector.class)) {
            retrievalService.findSimilarJobPostings(30L, 0);

            verify(statement).setInt(5, 1);
        }
    }

    @Test
    @DisplayName("cosine distance가 1보다 크면 similarity score를 0으로 제한한다")
    void clampNegativeSimilarityScoreToZero() throws Exception {
        JobPosting current = jobPosting(40L, 10L, "백엔드 개발자");
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(jobPostingRepository.findById(40L)).thenReturn(Optional.of(current));
        when(cohereEmbeddingClient.embedQuery(textBuilder.build(current))).thenReturn(new float[]{0.5f});
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(41L);
        when(resultSet.getString("posting_name")).thenReturn("유사 공고");
        when(resultSet.getString("company_name")).thenReturn("유사 회사");
        when(resultSet.getString("job_title")).thenReturn("서버 개발자");
        when(resultSet.getDouble("distance")).thenReturn(1.2);

        try (MockedStatic<PGvector> ignored = mockStatic(PGvector.class)) {
            List<JobPostingSimilarityResult> results = retrievalService.findSimilarJobPostings(40L);

            assertThat(results.getFirst().similarityScore()).isZero();
        }
    }

    @Test
    @DisplayName("존재하지 않는 현재 공고는 조회하지 않고 예외 처리한다")
    void currentJobPostingNotFound() {
        when(jobPostingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retrievalService.findSimilarJobPostings(404L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.JOB_POSTING_NOT_FOUND));
    }

    private JobPosting jobPosting(Long jobPostingId, Long userId, String jobTitle) {
        Classification classification = Classification.create("개발");
        DetailClassification detailClassification = classification
                .addMiddleClassification("서버")
                .addDetailClassification("백엔드");
        JobPosting jobPosting = JobPosting.create(
                User.authenticatedPrincipal(userId, "user-" + userId + "@example.com", UserRole.USER),
                Company.create("테스트 회사", CompanySize.MEDIUM),
                detailClassification,
                JobPostingProfileColor.DEFAULT,
                "테스트 공고",
                jobTitle,
                "Spring Boot API 개발",
                "Java, Spring, JPA",
                "AWS, Docker"
        );
        ReflectionTestUtils.setField(jobPosting, "id", jobPostingId);
        return jobPosting;
    }
}
