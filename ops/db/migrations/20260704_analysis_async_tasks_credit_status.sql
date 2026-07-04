ALTER TABLE analysis_async_tasks
    ADD COLUMN IF NOT EXISTS credit_reference_id VARCHAR(100);

ALTER TABLE analysis_async_tasks
    ADD COLUMN IF NOT EXISTS credit_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
