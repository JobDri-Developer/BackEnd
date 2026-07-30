CREATE INDEX IF NOT EXISTS idx_analysis_async_tasks_user_mock_apply_status
    ON analysis_async_tasks (user_id, mock_apply_id, status);
