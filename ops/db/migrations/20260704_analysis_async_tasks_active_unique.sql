CREATE UNIQUE INDEX IF NOT EXISTS uk_analysis_async_tasks_active_user_mock_apply
    ON analysis_async_tasks (user_id, mock_apply_id)
    WHERE status IN ('PENDING', 'RUNNING');
