CREATE TABLE IF NOT EXISTS master_resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    gpa NUMERIC(6, 3),
    max_gpa NUMERIC(6, 3),
    content_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_master_resumes_gpa CHECK (
        (gpa IS NULL AND max_gpa IS NULL)
        OR (gpa >= 0 AND max_gpa >= 0 AND gpa <= max_gpa)
    )
);

CREATE TABLE IF NOT EXISTS master_resume_metrics (
    master_resume_id BIGINT NOT NULL REFERENCES master_resumes(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    metric_type VARCHAR(20) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (master_resume_id, display_order),
    CONSTRAINT ck_master_resume_metrics_type CHECK (metric_type IN ('CERTIFICATE', 'LANGUAGE', 'AWARD', 'CUSTOM'))
);

CREATE TABLE IF NOT EXISTS master_resume_experiences (
    master_resume_id BIGINT NOT NULL REFERENCES master_resumes(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    experience_name VARCHAR(255) NOT NULL,
    period VARCHAR(100),
    description TEXT,
    PRIMARY KEY (master_resume_id, display_order)
);
