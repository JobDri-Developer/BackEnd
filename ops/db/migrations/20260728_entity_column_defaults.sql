-- Manual migration for persistent column defaults that must not depend on
-- Spring's schema.sql initialization lifecycle.

ALTER TABLE IF EXISTS analyses
    ALTER COLUMN missing_keywords SET DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ALTER COLUMN key_strengths SET DEFAULT '[]';

ALTER TABLE IF EXISTS analyses
    ALTER COLUMN key_weaknesses SET DEFAULT '[]';

ALTER TABLE IF EXISTS job_postings
    ALTER COLUMN profile_color SET DEFAULT 'DEFAULT';

ALTER TABLE IF EXISTS job_postings
    ALTER COLUMN posting_name SET DEFAULT '미입력';

ALTER TABLE IF EXISTS job_postings
    ALTER COLUMN job_title SET DEFAULT '미입력';
