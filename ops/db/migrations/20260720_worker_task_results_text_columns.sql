ALTER TABLE worker_task_results
    ALTER COLUMN result_payload TYPE TEXT,
    ALTER COLUMN last_error TYPE TEXT;
