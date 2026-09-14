ALTER TABLE job_applications
    ADD COLUMN IF NOT EXISTS gpa NUMERIC(6, 3),
    ADD COLUMN IF NOT EXISTS max_gpa NUMERIC(6, 3);

ALTER TABLE job_applications
    DROP CONSTRAINT IF EXISTS ck_job_applications_gpa;

ALTER TABLE job_applications
    ADD CONSTRAINT ck_job_applications_gpa CHECK (
        (gpa IS NULL AND max_gpa IS NULL)
        OR (gpa >= 0 AND max_gpa >= 0 AND gpa <= max_gpa)
    );

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
