package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusEmbeddingClient;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class AnalysisReferenceRetrievalService {

    @Value("${app.analysis.retrieval.jd-limit:3}")
    private int jdLimit;

    @Value("${app.analysis.retrieval.question-limit:5}")
    private int questionLimit;

    private final CorpusEmbeddingClient corpusEmbeddingClient;
    private final DataSource dataSource;

    public AnalysisReferenceContext retrieve(JobPosting jobPosting, List<Question> questions) {
        String jdQuery = buildJobPostingQuery(jobPosting);
        String questionQuery = buildQuestionQuery(jobPosting, questions);

        List<RetrievedJobPostingReference> jobPostingReferences = StringUtils.hasText(jdQuery)
                ? findSimilarJobPostings(jobPosting, jdQuery, jdLimit)
                : List.of();

        List<RetrievedQuestionReference> questionReferences = StringUtils.hasText(questionQuery)
                ? findSimilarQuestions(jobPosting, questionQuery, questionLimit)
                : List.of();

        return new AnalysisReferenceContext(jobPostingReferences, questionReferences);
    }

    private List<RetrievedJobPostingReference> findSimilarJobPostings(JobPosting jobPosting, String query, int limit) {
        String sql = """
                SELECT
                    c.id,
                    c.company_name,
                    c.role_l3,
                    c.responsibilities,
                    c.requirements,
                    c.preferred,
                    e.embedding <=> ? AS distance
                FROM mock_job_posting_embeddings e
                JOIN mock_job_posting_corpus c ON e.corpus_id = c.id
                WHERE c.is_valid_for_embedding = true
                  AND (c.detail_classification_id = ? OR c.job_group_l1 = ?)
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;

        float[] vector = corpusEmbeddingClient.embedQuery(query);
        List<RetrievedJobPostingReference> result = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, new PGvector(vector));
                statement.setObject(2, jobPosting.getDetailClassification().getId());
                statement.setString(3, jobPosting.getDetailClassification().getMiddleClassification().getClassification().getBigName());
                statement.setObject(4, new PGvector(vector));
                statement.setInt(5, limit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(new RetrievedJobPostingReference(
                                rs.getLong("id"),
                                rs.getString("company_name"),
                                rs.getString("role_l3"),
                                rs.getString("responsibilities"),
                                rs.getString("requirements"),
                                rs.getString("preferred"),
                                rs.getDouble("distance")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("유사 JD 검색 중 오류가 발생했습니다.", e);
        }

        return result;
    }

    private List<RetrievedQuestionReference> findSimilarQuestions(JobPosting jobPosting, String query, int limit) {
        String sql = """
                SELECT
                    c.id,
                    c.company_name,
                    c.role_l3,
                    c.question_type,
                    c.char_limit,
                    c.question_text,
                    e.embedding <=> ? AS distance
                FROM mock_question_embeddings e
                JOIN mock_question_corpus c ON e.corpus_id = c.id
                WHERE c.is_valid_for_embedding = true
                  AND (c.detail_classification_id = ? OR c.job_group_l1 = ?)
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;

        float[] vector = corpusEmbeddingClient.embedQuery(query);
        List<RetrievedQuestionReference> result = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, new PGvector(vector));
                statement.setObject(2, jobPosting.getDetailClassification().getId());
                statement.setString(3, jobPosting.getDetailClassification().getMiddleClassification().getClassification().getBigName());
                statement.setObject(4, new PGvector(vector));
                statement.setInt(5, limit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(new RetrievedQuestionReference(
                                rs.getLong("id"),
                                rs.getString("company_name"),
                                rs.getString("role_l3"),
                                rs.getString("question_type"),
                                getNullableInt(rs, "char_limit"),
                                rs.getString("question_text"),
                                rs.getDouble("distance")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("유사 문항 검색 중 오류가 발생했습니다.", e);
        }

        return result;
    }

    private Integer getNullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String buildJobPostingQuery(JobPosting jobPosting) {
        return """
                회사명: %s
                직무명: %s
                주요 업무:
                %s
                자격 요건:
                %s
                우대 사항:
                %s
                """.formatted(
                defaultString(jobPosting.getCompany().getName()),
                defaultString(jobPosting.getDetailClassification().getDetailName()),
                defaultString(jobPosting.getTask()),
                defaultString(jobPosting.getRequirement()),
                defaultString(jobPosting.getPreferred())
        ).trim();
    }

    private String buildQuestionQuery(JobPosting jobPosting, List<Question> questions) {
        String questionText = questions.stream()
                .map(Question::getContent)
                .filter(StringUtils::hasText)
                .map(text -> "- " + text)
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();

        return """
                회사명: %s
                직무명: %s
                자소서 문항:
                %s
                """.formatted(
                defaultString(jobPosting.getCompany().getName()),
                defaultString(jobPosting.getDetailClassification().getDetailName()),
                questionText
        ).trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record AnalysisReferenceContext(
            List<RetrievedJobPostingReference> jobPostingReferences,
            List<RetrievedQuestionReference> questionReferences
    ) {
    }

    public record RetrievedJobPostingReference(
            Long corpusId,
            String companyName,
            String roleName,
            String responsibilities,
            String requirements,
            String preferred,
            double distance
    ) {
    }

    public record RetrievedQuestionReference(
            Long corpusId,
            String companyName,
            String roleName,
            String questionType,
            Integer charLimit,
            String questionText,
            double distance
    ) {
    }
}
