CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS job_posting_embeddings (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL UNIQUE REFERENCES job_postings(id) ON DELETE CASCADE,
    embedding_model VARCHAR(100) NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_posting_embeddings_job_posting
    ON job_posting_embeddings (job_posting_id);

CREATE INDEX IF NOT EXISTS idx_job_posting_embeddings_hnsw
    ON job_posting_embeddings USING hnsw (embedding vector_cosine_ops);
