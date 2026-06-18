-- Manual migration to enforce credit transaction idempotency at the database level.
-- Run after backing up the database.

-- Remove duplicate rows that violate the intended uniqueness rule and keep the earliest row.
WITH ranked_duplicates AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, type, reference_id
            ORDER BY id
        ) AS duplicate_rank
    FROM credit_transactions
    WHERE reference_id IS NOT NULL
)
DELETE FROM credit_transactions
WHERE id IN (
    SELECT id
    FROM ranked_duplicates
    WHERE duplicate_rank > 1
);

-- Abort before adding the constraint if duplicates still remain for any reason.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM credit_transactions
        WHERE reference_id IS NOT NULL
        GROUP BY user_id, type, reference_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate credit_transactions remain for (user_id, type, reference_id); aborting unique constraint creation.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'credit_transactions'
          AND constraint_name = 'uk_credit_transactions_user_type_reference'
    ) THEN
        ALTER TABLE credit_transactions
            ADD CONSTRAINT uk_credit_transactions_user_type_reference
            UNIQUE (user_id, type, reference_id);
    END IF;
END $$;
