ALTER TABLE analyses
    ADD COLUMN IF NOT EXISTS input_fingerprint VARCHAR(64);
