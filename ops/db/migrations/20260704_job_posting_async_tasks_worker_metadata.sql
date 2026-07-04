ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(40);

ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(100);

ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS max_retry_count INTEGER NOT NULL DEFAULT 3;

ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;

ALTER TABLE job_posting_async_tasks
    ADD COLUMN IF NOT EXISTS queue_latency_millis BIGINT;
