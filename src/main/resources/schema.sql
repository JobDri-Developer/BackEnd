CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS job_applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_job_posting_id BIGINT REFERENCES job_postings(id) ON DELETE SET NULL,
    mock_apply_id BIGINT UNIQUE REFERENCES mock_applies(id) ON DELETE SET NULL,
    detail_classification_id BIGINT REFERENCES detail_classifications(id) ON DELETE SET NULL,
    company_name VARCHAR(255) NOT NULL,
    posting_name VARCHAR(255) NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    company_size VARCHAR(20),
    task TEXT,
    requirement TEXT,
    preferred TEXT,
    deadline_at TIMESTAMP,
    stage VARCHAR(20) NOT NULL,
    stage_order INTEGER NOT NULL,
    current_label VARCHAR(100),
    current_at TIMESTAMP,
    memo TEXT,
    gpa NUMERIC(6, 3),
    max_gpa NUMERIC(6, 3),
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_job_applications_stage
        CHECK (stage IN ('PLANNED', 'DOCUMENT', 'INTERVIEW', 'COMPLETED')),
    CONSTRAINT ck_job_applications_stage_order CHECK (stage_order >= 0),
    CONSTRAINT ck_job_applications_gpa CHECK (
        (gpa IS NULL AND max_gpa IS NULL)
        OR (gpa >= 0 AND max_gpa >= 0 AND gpa <= max_gpa)
    )
);

ALTER TABLE IF EXISTS job_applications
    ADD COLUMN IF NOT EXISTS gpa NUMERIC(6, 3),
    ADD COLUMN IF NOT EXISTS max_gpa NUMERIC(6, 3);

CREATE TABLE IF NOT EXISTS job_application_required_skills (
    job_application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    skill_name VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (job_application_id, display_order)
);

CREATE INDEX IF NOT EXISTS idx_job_applications_user_stage_order
    ON job_applications (user_id, stage, stage_order)
    WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_job_applications_user_archived
    ON job_applications (user_id, archived_at DESC)
    WHERE archived_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_job_applications_search
    ON job_applications USING gin (
        (company_name || ' ' || posting_name || ' ' || job_title) gin_trgm_ops
    );

CREATE TABLE IF NOT EXISTS job_application_essays (
    id BIGSERIAL PRIMARY KEY,
    job_application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    question VARCHAR(1000) NOT NULL,
    answer TEXT,
    display_order INTEGER NOT NULL CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_job_application_essays_application
    ON job_application_essays (job_application_id, display_order);

CREATE TABLE IF NOT EXISTS job_application_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    job_application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    content VARCHAR(200) NOT NULL,
    completed BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_job_application_checklist_application
    ON job_application_checklist_items (job_application_id, display_order);

CREATE TABLE IF NOT EXISTS job_application_metrics (
    id BIGSERIAL PRIMARY KEY,
    job_application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    metric_value VARCHAR(200) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    CONSTRAINT ck_job_application_metrics_type
        CHECK (type IN ('CERTIFICATE', 'LANGUAGE', 'AWARD', 'CUSTOM'))
);

CREATE INDEX IF NOT EXISTS idx_job_application_metrics_application
    ON job_application_metrics (job_application_id, display_order);

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

CREATE UNIQUE INDEX IF NOT EXISTS idx_analysis_async_tasks_credit_reference_id_unique
    ON analysis_async_tasks (credit_reference_id)
    WHERE credit_reference_id IS NOT NULL;

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

ALTER TABLE IF EXISTS analysis_async_tasks
    ADD COLUMN IF NOT EXISTS credit_reference_version INTEGER NOT NULL DEFAULT 0;

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
    ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(255);

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

CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_created_at_id_desc
    ON credit_transactions (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_type_created_at_id_desc
    ON credit_transactions (user_id, type, created_at DESC, id DESC);
