CREATE TABLE IF NOT EXISTS job_application_essays (
    id BIGSERIAL PRIMARY KEY,
    job_application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    question VARCHAR(1000) NOT NULL,
    answer TEXT,
    display_order INTEGER NOT NULL CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_job_application_essays_application
    ON job_application_essays (job_application_id, display_order);
