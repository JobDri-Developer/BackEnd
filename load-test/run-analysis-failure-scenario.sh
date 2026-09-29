#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
worker_image="${LOAD_TEST_WORKER_IMAGE:-jobdri-analysis-worker-loadtest:latest}"
docker_network="${LOAD_TEST_DOCKER_NETWORK:-backend_default}"
worker_name="${LOAD_TEST_WORKER_NAME:-jobdri-analysis-worker-failure-test}"
timeout_seconds="${LOAD_TEST_SCENARIO_TIMEOUT_SECONDS:-120}"
compose=(docker compose --env-file "$load_env_file" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest)
worker_timeout_args=()

case "$mode" in
  latency_20|latency_30|latency_60)
    stub_mode=success
    stub_latency_seconds="${mode#latency_}"
    stub_success_attempt=3
    worker_timeout_args=(-e OPENAI_TIMEOUT_SECONDS=300)
    expected_status=SUCCEEDED
    expected_retry_count=0
    expected_credit_status=CONFIRMED
    expected_failure_reason=""
    expected_analysis_count=1
    expected_dlq_count=0
    ;;
  timeout)
    stub_mode=success
    stub_latency_seconds="${LOAD_TEST_STUB_TIMEOUT_LATENCY_SECONDS:-2}"
    stub_success_attempt=3
    worker_timeout_args=(-e "OPENAI_TIMEOUT_SECONDS=${LOAD_TEST_WORKER_OPENAI_TIMEOUT_SECONDS:-1}")
    expected_status=FAILED
    expected_retry_count=4
    expected_credit_status=RELEASED
    expected_failure_reason=OPENAI_TIMEOUT
    expected_analysis_count=0
    expected_dlq_count=1
    ;;
  retry_then_success)
    stub_mode=retry_then_success
    stub_latency_seconds=0
    stub_success_attempt="${LOAD_TEST_STUB_SUCCESS_ATTEMPT:-7}"
    expected_status=SUCCEEDED
    expected_retry_count=2
    expected_credit_status=CONFIRMED
    expected_failure_reason=""
    expected_analysis_count=1
    expected_dlq_count=0
    ;;
  429)
    stub_mode=429
    stub_latency_seconds=0
    stub_success_attempt=3
    expected_status=FAILED
    expected_retry_count=4
    expected_credit_status=RELEASED
    expected_failure_reason=RATE_LIMIT
    expected_analysis_count=0
    expected_dlq_count=1
    ;;
  500|502|503)
    stub_mode="$mode"
    stub_latency_seconds=0
    stub_success_attempt=3
    expected_status=FAILED
    expected_retry_count=4
    expected_credit_status=RELEASED
    expected_failure_reason=INTERNAL_ERROR
    expected_analysis_count=0
    expected_dlq_count=1
    ;;
  invalid_json|semantic_invalid)
    stub_mode="$mode"
    stub_latency_seconds=0
    stub_success_attempt=3
    expected_status=FAILED
    expected_retry_count=0
    expected_credit_status=RELEASED
    expected_failure_reason=VALIDATION_ERROR
    expected_analysis_count=0
    expected_dlq_count=1
    ;;
  *)
    echo "usage: LOAD_TEST_ENV_FILE=/absolute/path/.env.loadtest $0 {latency_20|latency_30|latency_60|timeout|retry_then_success|429|500|502|503|invalid_json|semantic_invalid}" >&2
    exit 2
    ;;
esac

if ! [[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
  echo "LOAD_TEST_SCENARIO_TIMEOUT_SECONDS must be a positive integer" >&2
  exit 2
fi
if [[ ! -f "$load_env_file" ]]; then
  echo "load-test environment file not found: $load_env_file" >&2
  exit 2
fi

cleanup() {
  docker rm -f "$worker_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

export LOAD_TEST_STUB_MODE="$stub_mode"
export LOAD_TEST_STUB_LATENCY_SECONDS="$stub_latency_seconds"
export LOAD_TEST_STUB_SUCCESS_ATTEMPT="$stub_success_attempt"
"${compose[@]}" up -d postgres redis rabbitmq api
"${compose[@]}" up -d --build --no-deps --force-recreate ai-stub
"${compose[@]}" stop worker >/dev/null 2>&1 || true

for _ in $(seq 1 60); do
  if curl --silent --output /dev/null http://localhost:8080/actuator/health; then
    break
  fi
  sleep 1
done
curl --silent --output /dev/null http://localhost:8080/actuator/health || {
  echo "API did not become healthy" >&2
  exit 1
}

cleanup
consumer_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name consumers -q | awk '$1 == "jobdri.analysis.execute" { print $2 }')
if [[ "${consumer_count:-0}" != "0" ]]; then
  echo "refusing to run while jobdri.analysis.execute has active consumers: $consumer_count" >&2
  exit 1
fi
docker exec jobdri-rabbitmq rabbitmqctl purge_queue jobdri.analysis.execute >/dev/null 2>&1 || true
docker exec jobdri-rabbitmq rabbitmqctl purge_queue jobdri.analysis.execute.dlq >/dev/null 2>&1 || true
LOAD_TEST_ENV_FILE="$load_env_file" bash load-test/seed/seed.sh 1 >/dev/null

read -r access_token mock_apply_id < <(python3 - <<'PY'
values = {}
with open("load-test/results/k6.env", encoding="utf-8") as env_file:
    for raw_line in env_file:
        line = raw_line.rstrip("\n")
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
print(values["LOAD_TEST_ACCESS_TOKEN"], values["LOAD_TEST_MOCK_APPLY_IDS"].split(",")[0])
PY
)

response_file=$(mktemp)
http_status=$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
  --request POST "http://localhost:8080/api/mock-applies/$mock_apply_id/analysis" \
  --header "Authorization: Bearer $access_token")
if [[ "$http_status" != "200" && "$http_status" != "202" ]]; then
  echo "analysis submission failed with HTTP $http_status" >&2
  sed -n '1,10p' "$response_file" >&2
  rm -f "$response_file"
  exit 1
fi
rm -f "$response_file"

docker run -d --name "$worker_name" --network "$docker_network" \
  --env-file "$load_env_file" \
  -e WORKER_ENV=docker \
  -e RABBITMQ_HOST=rabbitmq \
  -e SPRING_API_BASE_URL=http://api:8080 \
  -e OPENAI_BASE_URL=http://ai-stub:18080/v1 \
  "${worker_timeout_args[@]}" \
  -e WORKER_PREFETCH_COUNT=1 \
  -e WORKER_ANALYSIS_CONCURRENCY_LIMIT=1 \
  -e WORKER_DEFAULT_CONCURRENCY_LIMIT=1 \
  "$worker_image" >/dev/null

task_row=""
for _ in $(seq 1 "$timeout_seconds"); do
  task_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
    "select status, retry_count, credit_status, coalesce(failure_reason::text, '') from analysis_async_tasks order by created_at desc limit 1")
  if [[ "$task_row" == SUCCEEDED\|* || "$task_row" == FAILED\|* || "$task_row" == CANCELLED\|* ]]; then
    break
  fi
  sleep 1
done

IFS='|' read -r actual_status actual_retry_count actual_credit_status actual_failure_reason <<< "$task_row"
actual_analysis_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select count(*) from analyses')
actual_dlq_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q | awk '$1 == "jobdri.analysis.execute.dlq" { print $2 }')
actual_dlq_count="${actual_dlq_count:-0}"

printf 'mode=%s status=%s retry_count=%s credit_status=%s failure_reason=%s analyses=%s dlq=%s\n' \
  "$mode" "$actual_status" "$actual_retry_count" "$actual_credit_status" \
  "${actual_failure_reason:--}" "$actual_analysis_count" "$actual_dlq_count"

failed=0
assert_equal() {
  local field="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "assertion failed: $field expected=$expected actual=$actual" >&2
    failed=1
  fi
}
assert_equal status "$expected_status" "$actual_status"
assert_equal retry_count "$expected_retry_count" "$actual_retry_count"
assert_equal credit_status "$expected_credit_status" "$actual_credit_status"
assert_equal failure_reason "$expected_failure_reason" "$actual_failure_reason"
assert_equal analyses "$expected_analysis_count" "$actual_analysis_count"
assert_equal dlq "$expected_dlq_count" "$actual_dlq_count"

if (( failed != 0 )); then
  echo "worker logs:" >&2
  docker logs --tail 80 "$worker_name" >&2
  exit 1
fi
