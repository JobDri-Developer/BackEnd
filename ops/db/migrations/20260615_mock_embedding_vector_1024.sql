-- Manual migration for environments that already have embedding tables created
-- with an unbounded vector column. Run after backing up the database.

CREATE EXTENSION IF NOT EXISTS vector;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'mock_job_posting_embeddings'
          AND column_name = 'embedding'
    ) THEN
        ALTER TABLE mock_job_posting_embeddings
            ALTER COLUMN embedding TYPE vector(1024);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'mock_question_embeddings'
          AND column_name = 'embedding'
    ) THEN
        ALTER TABLE mock_question_embeddings
            ALTER COLUMN embedding TYPE vector(1024);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_mock_job_posting_embeddings_hnsw
    ON mock_job_posting_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_mock_question_embeddings_hnsw
    ON mock_question_embeddings USING hnsw (embedding vector_cosine_ops);
