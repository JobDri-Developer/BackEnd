#!/usr/bin/env bash
set -euo pipefail
umask 077

fixture_count="${1:-6060}"
user_count="${2:-1}"
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
result_dir="${LOAD_TEST_RESULT_DIR:-load-test/results}"

if ! [[ "$fixture_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "fixture count must be a positive integer" >&2
  exit 1
fi
if ! [[ "$user_count" =~ ^[1-9][0-9]*$ ]] || (( user_count > fixture_count )); then
  echo "user count must be a positive integer no greater than fixture count" >&2
  exit 1
fi

database_name=$(docker exec jobdri-postgres \
  psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select current_database()')
if [[ "$database_name" != "jobdri_loadtest" ]]; then
  echo "refusing to seed non-load-test database: $database_name" >&2
  exit 1
fi

docker exec -i jobdri-postgres psql -v ON_ERROR_STOP=1 \
  -v fixture_count="$fixture_count" -v user_count="$user_count" \
  -U jobdri_loadtest -d jobdri_loadtest <<'SQL'
DO $$
DECLARE
    table_list text;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
      INTO table_list
      FROM pg_tables
     WHERE schemaname = 'public';
    IF table_list IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE ' || table_list || ' RESTART IDENTITY CASCADE';
    END IF;
END $$;

INSERT INTO classifications (big_name) VALUES ('IT_LOAD_TEST');
INSERT INTO middle_classifications (classification_id, middle_name)
VALUES (currval('classifications_id_seq'), '백엔드');
INSERT INTO detail_classifications (middle_classification_id, detail_name)
VALUES (currval('middle_classifications_id_seq'), 'Java');
INSERT INTO companies (created_at, updated_at, name, size)
VALUES (now(), now(), 'JobDri Load Test', 'STARTUP');
INSERT INTO users (credit, created_at, updated_at, email, name, password, role, social_type)
SELECT ceil(:fixture_count::numeric / :user_count)::bigint + 1000,
       now(), now(), 'loadtest+' || n || '@jobdri.invalid', 'Load Test ' || n,
       'unused', 'USER', 'LOCAL'
  FROM generate_series(1, :user_count) AS n;
INSERT INTO job_postings (
    company_id, created_at, detail_classification_id, updated_at, user_id,
    profile_color, job_title, posting_name, preferred, requirement, task
)
SELECT currval('companies_id_seq'), now(), currval('detail_classifications_id_seq'), now(),
       u.id, 'DEFAULT', '백엔드 개발자', '부하 테스트 전용 공고 ' || u.id,
       '대용량 트래픽 경험', 'Java와 Spring 경험', '채용 서비스 API 개발'
  FROM users u
 WHERE u.email LIKE 'loadtest+%@jobdri.invalid';
WITH inserted AS (
    INSERT INTO mock_applies (
        sequence, created_at, updated_at, job_posting_id, user_id,
        display_name, apply_type, status
    )
    SELECT ((n - 1) / :user_count) + 1, now(), now(), jp.id, u.id,
           '부하 테스트 지원서 ' || n, 'MOCK', 'ANSWER_WRITE'
      FROM generate_series(1, :fixture_count) AS n
      JOIN users u ON u.email = 'loadtest+' || (((n - 1) % :user_count) + 1) || '@jobdri.invalid'
      JOIN job_postings jp ON jp.user_id = u.id
    RETURNING id
)
INSERT INTO questions (
    char_limit, created_at, updated_at, mock_apply_id, answer, content
)
SELECT 1000, now(), now(), id,
       'Spring 기반 서비스에서 병목을 측정하고 큐를 활용해 비동기 처리량을 개선했습니다.',
       '지원 직무와 관련된 성과를 설명해 주세요.'
  FROM inserted;
SQL

mkdir -p "$result_dir"
docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
  'select string_agg(id::text, '"'"','"'"' order by id) from mock_applies' \
  > "$result_dir/mock-apply-ids.txt"
docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF $'\t' -c \
  "select ma.id, u.id, u.email from mock_applies ma join users u on u.id = ma.user_id order by ma.id" \
  > "$result_dir/mock-apply-cases.tsv"

python3 - "$load_env_file" "$result_dir/mock-apply-cases.tsv" "$result_dir/k6.env" <<'PY'
import base64
import hashlib
import hmac
import json
import sys
import time

env_path, cases_path, output_path = sys.argv[1:]
values = {}
with open(env_path, encoding="utf-8") as env_file:
    for raw_line in env_file:
        line = raw_line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value

secret = base64.b64decode(values["JWT_SECRET_KEY"])
now = int(time.time())

def encode(payload):
    encoded = json.dumps(payload, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(encoded).rstrip(b"=").decode()

def create_token(user_id, email):
    unsigned = ".".join((
        encode({"alg": "HS256"}),
        encode({
            "sub": email,
            "iat": now,
            "exp": now + 7200,
            "userId": user_id,
            "role": "USER",
        }),
    ))
    signature = base64.urlsafe_b64encode(
        hmac.new(secret, unsigned.encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return f"{unsigned}.{signature}"

with open(cases_path, encoding="utf-8") as cases_file:
    rows = [line.rstrip("\n").split("\t") for line in cases_file if line.strip()]
users = sorted({(int(user_id), email) for _, user_id, email in rows})
token_indexes = {user_id: index for index, (user_id, _) in enumerate(users)}
tokens = [create_token(user_id, email) for user_id, email in users]
ids = ",".join(mock_apply_id for mock_apply_id, _, _ in rows)
cases = ",".join(
    f"{mock_apply_id}:{token_indexes[int(user_id)]}"
    for mock_apply_id, user_id, _ in rows
)
with open(output_path, "w", encoding="utf-8") as output:
    output.write("LOAD_TEST_BASE_URL=http://api:8080\n")
    output.write(f"LOAD_TEST_ACCESS_TOKEN={tokens[0]}\n")
    output.write(f"LOAD_TEST_MOCK_APPLY_IDS={ids}\n")
    output.write(f"LOAD_TEST_ACCESS_TOKENS={','.join(tokens)}\n")
    output.write(f"LOAD_TEST_MOCK_APPLY_CASES={cases}\n")
    output.write(f"LOAD_TEST_USER_COUNT={len(tokens)}\n")
PY

echo "seeded $fixture_count synthetic mock applies across $user_count users"
echo "k6 variables: $result_dir/k6.env"
