package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingSimilarityResult;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingRetrievalService {

    private static final int DEFAULT_LIMIT = 3;
    private static final String SIMILAR_JOB_POSTINGS_SQL = """
            SELECT
                jp.id,
                jp.posting_name,
                c.name AS company_name,
                jp.job_title,
                e.embedding <=> ? AS distance
            FROM job_posting_embeddings e
            JOIN job_postings jp ON e.job_posting_id = jp.id
            JOIN companies c ON jp.company_id = c.id
            WHERE jp.id <> ?
              AND jp.user_id = ?
            ORDER BY e.embedding <=> ?
            LIMIT ?
            """;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingEmbeddingTextBuilder textBuilder;
    private final CohereEmbeddingClient cohereEmbeddingClient;
    private final DataSource dataSource;

    public List<JobPostingSimilarityResult> findSimilarJobPostings(Long jobPostingId) {
        return findSimilarJobPostings(jobPostingId, DEFAULT_LIMIT);
    }

    public List<JobPostingSimilarityResult> findSimilarJobPostings(Long jobPostingId, int limit) {
        JobPosting current = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "해당 공고를 찾을 수 없습니다. jobPostingId=" + jobPostingId
                ));
        int actualLimit = Math.max(1, limit);
        String query = textBuilder.build(current);
        float[] vector = cohereEmbeddingClient.embedQuery(query);
        return findSimilarJobPostings(current, vector, actualLimit);
    }

    private List<JobPostingSimilarityResult> findSimilarJobPostings(
            JobPosting current,
            float[] vector,
            int limit
    ) {
        try (Connection connection = dataSource.getConnection()) {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(SIMILAR_JOB_POSTINGS_SQL)) {
                statement.setObject(1, new PGvector(vector));
                statement.setLong(2, current.getId());
                statement.setLong(3, current.getUser().getId());
                statement.setObject(4, new PGvector(vector));
                statement.setInt(5, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<JobPostingSimilarityResult> results = new ArrayList<>();
                    while (resultSet.next()) {
                        double distance = resultSet.getDouble("distance");
                        results.add(new JobPostingSimilarityResult(
                                resultSet.getLong("id"),
                                resultSet.getString("posting_name"),
                                resultSet.getString("company_name"),
                                resultSet.getString("job_title"),
                                1.0 - distance
                        ));
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("유사 채용 공고 검색 중 오류가 발생했습니다.", e);
        }
    }
}
