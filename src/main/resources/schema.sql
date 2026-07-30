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

CREATE TABLE IF NOT EXISTS job_posting_embeddings (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL UNIQUE REFERENCES job_postings(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_job_posting_embeddings_job_posting
    ON job_posting_embeddings (job_posting_id);

CREATE INDEX IF NOT EXISTS idx_mock_job_posting_embeddings_hnsw
    ON mock_job_posting_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_mock_question_embeddings_hnsw
    ON mock_question_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_job_posting_embeddings_hnsw
    ON job_posting_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_analysis_async_tasks_user_mock_apply_status
    ON analysis_async_tasks (user_id, mock_apply_id, status);

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS missing_keywords TEXT NOT NULL DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS key_strengths TEXT NOT NULL DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS key_weaknesses TEXT NOT NULL DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ADD COLUMN IF NOT EXISTS input_fingerprint VARCHAR(64);

-- Column defaults that must also exist outside Spring SQL initialization
-- are managed in ops/db/migrations so profiles like analysis-eval do not
-- implicitly depend on this schema.sql contract.

UPDATE job_postings
SET profile_color = 'DEFAULT'
WHERE profile_color IS NULL;

UPDATE job_postings jp
SET job_title = dc.detail_name
FROM detail_classifications dc
WHERE jp.detail_classification_id = dc.id
  AND (jp.job_title IS NULL OR jp.job_title = '미입력');

UPDATE job_postings
SET posting_name = job_title
WHERE posting_name IS NULL OR posting_name = '미입력';

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS current_step VARCHAR(60);

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS progress_percent INTEGER;

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS estimated_remaining_seconds INTEGER;

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS execution_context_snapshot TEXT;

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS input_fingerprint_snapshot VARCHAR(64);

ALTER TABLE IF EXISTS job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE IF EXISTS job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

ALTER TABLE IF EXISTS job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS current_step VARCHAR(60);

ALTER TABLE IF EXISTS job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS progress_percent INTEGER;

ALTER TABLE IF EXISTS job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS estimated_remaining_seconds INTEGER;

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS pay_token VARCHAR(50);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS checkout_page VARCHAR(500);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS toss_status VARCHAR(50);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS external_payment_id VARCHAR(255);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS external_transaction_id VARCHAR(255);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS external_status VARCHAR(50);

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS callback_received_at TIMESTAMP;

ALTER TABLE IF EXISTS payments
    ADD COLUMN IF NOT EXISTS last_status_checked_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_pay_token_unique
    ON payments (pay_token)
    WHERE pay_token IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_external_payment_id_unique
    ON payments (external_payment_id)
    WHERE external_payment_id IS NOT NULL;
