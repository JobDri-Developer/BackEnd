CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS mock_job_posting_embeddings (
    id BIGSERIAL PRIMARY KEY,
    corpus_id BIGINT NOT NULL UNIQUE REFERENCES mock_job_posting_corpus(id) ON DELETE CASCADE,
    embedding_model VARCHAR(100) NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mock_question_embeddings (
    id BIGSERIAL PRIMARY KEY,
    corpus_id BIGINT NOT NULL UNIQUE REFERENCES mock_question_corpus(id) ON DELETE CASCADE,
    embedding_model VARCHAR(100) NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_postings_company_detail
    ON job_postings (company_id, detail_classification_id);

CREATE INDEX IF NOT EXISTS idx_job_postings_detail
    ON job_postings (detail_classification_id);

CREATE INDEX IF NOT EXISTS idx_job_postings_company
    ON job_postings (company_id);

CREATE INDEX IF NOT EXISTS idx_mock_job_posting_embeddings_corpus
    ON mock_job_posting_embeddings (corpus_id);

CREATE INDEX IF NOT EXISTS idx_mock_question_embeddings_corpus
    ON mock_question_embeddings (corpus_id);

CREATE INDEX IF NOT EXISTS idx_mock_job_posting_embeddings_hnsw
    ON mock_job_posting_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_mock_question_embeddings_hnsw
    ON mock_question_embeddings USING hnsw (embedding vector_cosine_ops);

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS missing_keywords TEXT NOT NULL DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS key_strengths TEXT NOT NULL DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS key_weaknesses TEXT NOT NULL DEFAULT '[]';
