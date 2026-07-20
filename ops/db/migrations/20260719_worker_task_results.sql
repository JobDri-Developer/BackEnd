CREATE TABLE IF NOT EXISTS worker_task_results (
    task_id VARCHAR(36) PRIMARY KEY,
    task_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_payload TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    last_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);
