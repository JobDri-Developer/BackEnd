ALTER TABLE job_applications
    ADD COLUMN IF NOT EXISTS ingest_idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_applications_user_ingest_key
    ON job_applications (user_id, ingest_idempotency_key)
    WHERE ingest_idempotency_key IS NOT NULL;
