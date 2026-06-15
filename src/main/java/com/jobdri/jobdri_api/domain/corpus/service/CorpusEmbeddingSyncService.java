package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.domain.corpus.dto.response.CorpusEmbeddingSyncResponse;
import com.jobdri.jobdri_api.domain.corpus.entity.MockJobPostingCorpus;
import com.jobdri.jobdri_api.domain.corpus.entity.MockQuestionCorpus;
import com.jobdri.jobdri_api.domain.corpus.repository.MockJobPostingCorpusRepository;
import com.jobdri.jobdri_api.domain.corpus.repository.MockQuestionCorpusRepository;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CorpusEmbeddingSyncService {

    private static final String UPSERT_JOB_POSTING_SQL = """
            INSERT INTO mock_job_posting_embeddings (corpus_id, embedding_model, embedding, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (corpus_id)
            DO UPDATE SET
                embedding_model = EXCLUDED.embedding_model,
                embedding = EXCLUDED.embedding,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String UPSERT_QUESTION_SQL = """
            INSERT INTO mock_question_embeddings (corpus_id, embedding_model, embedding, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (corpus_id)
            DO UPDATE SET
                embedding_model = EXCLUDED.embedding_model,
                embedding = EXCLUDED.embedding,
                updated_at = EXCLUDED.updated_at
            """;

    @Value("${app.corpus.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${app.corpus.embedding.batch-size:32}")
    private int batchSize;

    private final MockJobPostingCorpusRepository mockJobPostingCorpusRepository;
    private final MockQuestionCorpusRepository mockQuestionCorpusRepository;
    private final CorpusEmbeddingClient corpusEmbeddingClient;
    private final DataSource dataSource;

    @Transactional(readOnly = true)
    public CorpusEmbeddingSyncResponse syncAll(Integer limit) {
        int jobPostingCount = syncJobPostingEmbeddings(limit);
        int questionCount = syncQuestionEmbeddings(limit);
        return new CorpusEmbeddingSyncResponse(jobPostingCount, questionCount, embeddingModel);
    }

    @Transactional(readOnly = true)
    public int syncJobPostingEmbeddings(Integer limit) {
        List<MockJobPostingCorpus> all = mockJobPostingCorpusRepository
                .findAllByValidForEmbeddingTrueAndEmbeddingTextIsNotNullOrderByIdAsc();
        List<MockJobPostingCorpus> corpusList = applyLimit(all, limit);
        return upsertJobPostingEmbeddings(corpusList);
    }

    @Transactional(readOnly = true)
    public int syncQuestionEmbeddings(Integer limit) {
        List<MockQuestionCorpus> all = mockQuestionCorpusRepository
                .findAllByValidForEmbeddingTrueAndEmbeddingTextIsNotNullOrderByIdAsc();
        List<MockQuestionCorpus> corpusList = applyLimit(all, limit);
        return upsertQuestionEmbeddings(corpusList);
    }

    private int upsertJobPostingEmbeddings(List<MockJobPostingCorpus> corpusList) {
        int processed = 0;
        for (List<MockJobPostingCorpus> batch : partition(corpusList, batchSize)) {
            List<float[]> embeddings = corpusEmbeddingClient.embed(
                    batch.stream().map(MockJobPostingCorpus::getEmbeddingText).toList()
            );
            upsertVectors(UPSERT_JOB_POSTING_SQL, batch.stream().map(MockJobPostingCorpus::getId).toList(), embeddings);
            processed += batch.size();
        }
        return processed;
    }

    private int upsertQuestionEmbeddings(List<MockQuestionCorpus> corpusList) {
        int processed = 0;
        for (List<MockQuestionCorpus> batch : partition(corpusList, batchSize)) {
            List<float[]> embeddings = corpusEmbeddingClient.embed(
                    batch.stream().map(MockQuestionCorpus::getEmbeddingText).toList()
            );
            upsertVectors(UPSERT_QUESTION_SQL, batch.stream().map(MockQuestionCorpus::getId).toList(), embeddings);
            processed += batch.size();
        }
        return processed;
    }

    private void upsertVectors(String sql, List<Long> ids, List<float[]> embeddings) {
        if (ids.size() != embeddings.size()) {
            throw new IllegalStateException("임베딩 결과 개수가 corpus 개수와 일치하지 않습니다.");
        }

        try (Connection connection = dataSource.getConnection()) {
            PGvector.registerTypes(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                for (int i = 0; i < ids.size(); i++) {
                    statement.setLong(1, ids.get(i));
                    statement.setString(2, embeddingModel);
                    statement.setObject(3, new PGvector(embeddings.get(i)));
                    statement.setTimestamp(4, now);
                    statement.setTimestamp(5, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("임베딩 벡터 저장 중 오류가 발생했습니다.", e);
        }
    }

    private <T> List<T> applyLimit(List<T> items, Integer limit) {
        if (limit == null || limit >= items.size()) {
            return items;
        }
        return items.subList(0, limit);
    }

    private <T> List<List<T>> partition(List<T> items, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        if (items.isEmpty()) {
            return result;
        }
        int actualBatchSize = Math.max(1, batchSize);
        for (int i = 0; i < items.size(); i += actualBatchSize) {
            result.add(items.subList(i, Math.min(items.size(), i + actualBatchSize)));
        }
        return result;
    }
}
