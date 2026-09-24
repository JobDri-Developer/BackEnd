ALTER TABLE job_applications
    ADD COLUMN IF NOT EXISTS ingest_idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_applications_user_ingest_key
    ON job_applications (user_id, ingest_idempotency_key)
    WHERE ingest_idempotency_key IS NOT NULL;

-- 운영 중 대용량 테이블에 서비스 트래픽을 유지하며 적용해야 한다면 위 CREATE INDEX 대신
-- 트랜잭션 블록 밖에서 다음 문장을 수동 실행할 수 있다.
-- CREATE UNIQUE INDEX CONCURRENTLY uk_job_applications_user_ingest_key
--     ON job_applications (user_id, ingest_idempotency_key)
--     WHERE ingest_idempotency_key IS NOT NULL;
-- concurrent 생성 실패로 INVALID 인덱스가 남으면 아래 조회로 상태를 확인한 뒤 삭제하고 재시도한다.
-- SELECT indexrelid::regclass, indisvalid FROM pg_index
--     WHERE indexrelid = 'uk_job_applications_user_ingest_key'::regclass;
-- DROP INDEX CONCURRENTLY IF EXISTS uk_job_applications_user_ingest_key;
-- 필요한 경우 운영자가 실행 세션에 적절한 lock_timeout을 별도로 설정한다.
