package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
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
public class CorpusRetrievalService {

    public static final String MOCK_BASE_QUERY_TEMPLATE = """
            직무명: %s
            중분류: %s
            대분류: %s
            회사명: %s
            """;

    @Value("${app.analysis.retrieval.jd-limit:3}")
    private int jdLimit;

    @Value("${app.analysis.retrieval.question-limit:5}")
    private int questionLimit;

    private final CorpusEmbeddingClient corpusEmbeddingClient;
    private final DataSource dataSource;

    public RetrievalContext retrieveForAnalysis(JobPosting jobPosting, List<Question> questions) {
        String jdQuery = buildAnalysisJobPostingQuery(jobPosting);
        String questionQuery = buildAnalysisQuestionQuery(jobPosting, questions);

        return new RetrievalContext(
                StringUtils.hasText(jdQuery) ? findSimilarJobPostings(jobPosting.getCompany(), jobPosting.getDetailClassification(), jdQuery, jdLimit) : List.of(),
                StringUtils.hasText(questionQuery) ? findSimilarQuestions(jobPosting.getCompany(), jobPosting.getDetailClassification(), questionQuery, questionLimit) : List.of()
        );
    }

    public RetrievalContext retrieveForMockGeneration(Company company, DetailClassification detailClassification) {
        String baseQuery = buildMockBaseQuery(company, detailClassification);
        float[] vector = corpusEmbeddingClient.embedQuery(baseQuery);
        return new RetrievalContext(
                findSimilarJobPostings(company, detailClassification, baseQuery, vector, jdLimit),
                findSimilarQuestions(company, detailClassification, baseQuery, vector, questionLimit)
        );
    }

    private List<RetrievedJobPostingReference> findSimilarJobPostings(
            Company company,
            DetailClassification detailClassification,
            String query,
            int limit
    ) {
        return findSimilarJobPostings(
                company,
                detailClassification,
                query,
                corpusEmbeddingClient.embedQuery(query),
                limit
        );
    }

    private List<RetrievedJobPostingReference> findSimilarJobPostings(
            Company company,
            DetailClassification detailClassification,
            String query,
            float[] vector,
            int limit
    ) {
        String companyAndDetailSql = """
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
                  AND c.detail_classification_id = ?
                  AND lower(c.company_name) = lower(?)
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;
        String detailOnlySql = """
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
                  AND c.detail_classification_id = ?
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;
        String hierarchySql = """
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
                  AND c.job_group_l1 = ?
                  AND c.job_family_l2 = ?
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection()) {
            PGvector.registerTypes(connection);

            List<RetrievedJobPostingReference> companyAndDetail = queryJobPostingReferences(
                    connection,
                    companyAndDetailSql,
                    vector,
                    statement -> {
                        statement.setObject(2, detailClassification.getId());
                        statement.setString(3, company.getName());
                        statement.setObject(4, new PGvector(vector));
                        statement.setInt(5, limit);
                    }
            );
            if (!companyAndDetail.isEmpty()) {
                return companyAndDetail;
            }

            List<RetrievedJobPostingReference> detailOnly = queryJobPostingReferences(
                    connection,
                    detailOnlySql,
                    vector,
                    statement -> {
                        statement.setObject(2, detailClassification.getId());
                        statement.setObject(3, new PGvector(vector));
                        statement.setInt(4, limit);
                    }
            );
            if (!detailOnly.isEmpty()) {
                return detailOnly;
            }

            return queryJobPostingReferences(
                    connection,
                    hierarchySql,
                    vector,
                    statement -> {
                        statement.setString(2, detailClassification.getMiddleClassification().getClassification().getBigName());
                        statement.setString(3, detailClassification.getMiddleClassification().getMiddleName());
                        statement.setObject(4, new PGvector(vector));
                        statement.setInt(5, limit);
                    }
            );
        } catch (SQLException e) {
            throw new IllegalStateException("유사 JD 검색 중 오류가 발생했습니다.", e);
        }
    }

    private List<RetrievedQuestionReference> findSimilarQuestions(
            Company company,
            DetailClassification detailClassification,
            String query,
            int limit
    ) {
        return findSimilarQuestions(
                company,
                detailClassification,
                query,
                corpusEmbeddingClient.embedQuery(query),
                limit
        );
    }

    private List<RetrievedQuestionReference> findSimilarQuestions(
            Company company,
            DetailClassification detailClassification,
            String query,
            float[] vector,
            int limit
    ) {
        String companyAndDetailSql = """
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
                  AND c.detail_classification_id = ?
                  AND lower(c.company_name) = lower(?)
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;
        String detailOnlySql = """
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
                  AND c.detail_classification_id = ?
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;
        String hierarchySql = """
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
                  AND c.job_group_l1 = ?
                  AND c.job_family_l2 = ?
                ORDER BY e.embedding <=> ?
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection()) {
            PGvector.registerTypes(connection);

            List<RetrievedQuestionReference> companyAndDetail = queryQuestionReferences(
                    connection,
                    companyAndDetailSql,
                    vector,
                    statement -> {
                        statement.setObject(2, detailClassification.getId());
                        statement.setString(3, company.getName());
                        statement.setObject(4, new PGvector(vector));
                        statement.setInt(5, limit);
                    }
            );
            if (!companyAndDetail.isEmpty()) {
                return companyAndDetail;
            }

            List<RetrievedQuestionReference> detailOnly = queryQuestionReferences(
                    connection,
                    detailOnlySql,
                    vector,
                    statement -> {
                        statement.setObject(2, detailClassification.getId());
                        statement.setObject(3, new PGvector(vector));
                        statement.setInt(4, limit);
                    }
            );
            if (!detailOnly.isEmpty()) {
                return detailOnly;
            }

            return queryQuestionReferences(
                    connection,
                    hierarchySql,
                    vector,
                    statement -> {
                        statement.setString(2, detailClassification.getMiddleClassification().getClassification().getBigName());
                        statement.setString(3, detailClassification.getMiddleClassification().getMiddleName());
                        statement.setObject(4, new PGvector(vector));
                        statement.setInt(5, limit);
                    }
            );
        } catch (SQLException e) {
            throw new IllegalStateException("유사 문항 검색 중 오류가 발생했습니다.", e);
        }
    }

    private List<RetrievedJobPostingReference> queryJobPostingReferences(
            Connection connection,
            String sql,
            float[] vector,
            StatementBinder binder
    ) throws SQLException {
        List<RetrievedJobPostingReference> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, new PGvector(vector));
            binder.bind(statement);
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
        return result;
    }

    private List<RetrievedQuestionReference> queryQuestionReferences(
            Connection connection,
            String sql,
            float[] vector,
            StatementBinder binder
    ) throws SQLException {
        List<RetrievedQuestionReference> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, new PGvector(vector));
            binder.bind(statement);
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
        return result;
    }

    private Integer getNullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String buildAnalysisJobPostingQuery(JobPosting jobPosting) {
        return """
                직무명: %s
                자격 요건: %s
                우대 사항: %s
                주요 업무: %s
                핵심 요구 역량 요약: %s
                우대 역량 요약: %s
                참고 회사명: %s
                """.formatted(
                defaultString(jobPosting.getDetailClassification().getDetailName()),
                defaultString(jobPosting.getRequirement()),
                defaultString(jobPosting.getPreferred()),
                defaultString(jobPosting.getTask()),
                defaultString(jobPosting.getRequirement()),
                defaultString(jobPosting.getPreferred()),
                defaultString(jobPosting.getCompany().getName())
        ).trim();
    }

    private String buildAnalysisQuestionQuery(JobPosting jobPosting, List<Question> questions) {
        String questionText = questions.stream()
                .map(Question::getContent)
                .filter(StringUtils::hasText)
                .map(text -> "- " + text)
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();

        return """
                직무명: %s
                자격 요건: %s
                우대 사항: %s
                자소서 문항:
                %s
                참고 회사명: %s
                """.formatted(
                defaultString(jobPosting.getDetailClassification().getDetailName()),
                defaultString(jobPosting.getRequirement()),
                defaultString(jobPosting.getPreferred()),
                questionText,
                defaultString(jobPosting.getCompany().getName())
        ).trim();
    }

    private String buildMockBaseQuery(Company company, DetailClassification detailClassification) {
        return MOCK_BASE_QUERY_TEMPLATE.formatted(
                defaultString(detailClassification.getDetailName()),
                defaultString(detailClassification.getMiddleClassification().getMiddleName()),
                defaultString(detailClassification.getMiddleClassification().getClassification().getBigName()),
                defaultString(company.getName())
        ).trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record RetrievalContext(
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

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
