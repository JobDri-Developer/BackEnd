CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_job_postings_company_detail
    ON job_postings (company_id, detail_classification_id);

CREATE INDEX IF NOT EXISTS idx_job_postings_detail
    ON job_postings (detail_classification_id);

CREATE INDEX IF NOT EXISTS idx_job_postings_company
    ON job_postings (company_id);
