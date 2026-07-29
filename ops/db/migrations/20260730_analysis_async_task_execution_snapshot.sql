ALTER TABLE analysis_async_tasks
    ADD COLUMN IF NOT EXISTS execution_context_snapshot TEXT;

ALTER TABLE analysis_async_tasks
    ADD COLUMN IF NOT EXISTS input_fingerprint_snapshot VARCHAR(64);
