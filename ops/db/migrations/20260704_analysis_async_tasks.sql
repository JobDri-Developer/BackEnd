CREATE TABLE IF NOT EXISTS analysis_async_tasks (
    task_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mock_apply_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(255) NOT NULL,
    error VARCHAR(2000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_async_tasks_user_mock_apply
    ON analysis_async_tasks (user_id, mock_apply_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_analysis_async_tasks_status
    ON analysis_async_tasks (status);
