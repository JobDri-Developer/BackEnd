ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_job_posting_async_tasks_user_id_created_at
    ON job_posting_async_tasks (user_id, created_at DESC);
