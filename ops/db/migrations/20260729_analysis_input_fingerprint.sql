DO $$
DECLARE
    previous_lock_timeout TEXT := current_setting('lock_timeout', true);
BEGIN
    PERFORM set_config('lock_timeout', '5s', false);

    EXECUTE $ddl$
        ALTER TABLE analyses
            ADD COLUMN IF NOT EXISTS input_fingerprint VARCHAR(64)
    $ddl$;

    PERFORM set_config('lock_timeout', COALESCE(NULLIF(previous_lock_timeout, ''), '0'), false);
EXCEPTION
    WHEN OTHERS THEN
        PERFORM set_config('lock_timeout', COALESCE(NULLIF(previous_lock_timeout, ''), '0'), false);
        RAISE;
END
$$;
