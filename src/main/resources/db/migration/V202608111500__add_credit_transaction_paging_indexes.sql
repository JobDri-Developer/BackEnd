CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_created_at_id_desc
    ON credit_transactions (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_type_created_at_id_desc
    ON credit_transactions (user_id, type, created_at DESC, id DESC);
