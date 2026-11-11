CREATE TABLE IF NOT EXISTS job_application_tasks (
    id BIGSERIAL PRIMARY KEY,
    job_application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    due_date DATE,
    memo VARCHAR(2000),
    completed BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_job_application_tasks_application
    ON job_application_tasks (job_application_id, id);

CREATE TABLE IF NOT EXISTS job_application_task_checklist_items (
    task_id BIGINT NOT NULL REFERENCES job_application_tasks(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    content VARCHAR(200) NOT NULL,
    completed BOOLEAN NOT NULL,
    PRIMARY KEY (task_id, display_order)
);
