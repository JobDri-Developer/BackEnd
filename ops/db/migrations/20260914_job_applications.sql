CREATE EXTENSION IF NOT EXISTS pg_trgm;

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
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_job_applications_stage
        CHECK (stage IN ('PLANNED', 'DOCUMENT', 'INTERVIEW', 'COMPLETED')),
    CONSTRAINT ck_job_applications_stage_order CHECK (stage_order >= 0)
);

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
