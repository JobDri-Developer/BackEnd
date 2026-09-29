#!/usr/bin/env bash
set -euo pipefail

backlog_count="${1:-}"
worker_concurrency="${2:-}"
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
worker_image="${LOAD_TEST_WORKER_IMAGE:-jobdri-analysis-worker-loadtest:latest}"
docker_network="${LOAD_TEST_DOCKER_NETWORK:-backend_default}"
worker_name="${LOAD_TEST_WORKER_NAME:-jobdri-analysis-worker-drain-test}"
timeout_seconds="${LOAD_TEST_SCENARIO_TIMEOUT_SECONDS:-600}"
result_dir="${LOAD_TEST_RESULT_DIR:-load-test/results}"
compose=(docker compose --env-file "$load_env_file" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest)

if ! [[ "$backlog_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "usage: LOAD_TEST_ENV_FILE=/absolute/path/.env.loadtest $0 BACKLOG_COUNT WORKER_CONCURRENCY" >&2
  exit 2
fi
if ! [[ "$worker_concurrency" =~ ^[1-9][0-9]*$ ]]; then
  echo "WORKER_CONCURRENCY must be a positive integer" >&2
  exit 2
fi
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

export LOAD_TEST_STUB_MODE=success
export LOAD_TEST_STUB_LATENCY_SECONDS="${LOAD_TEST_STUB_LATENCY_SECONDS:-0}"
"${compose[@]}" up -d postgres redis rabbitmq api
"${compose[@]}" up -d --build --no-deps --force-recreate ai-stub
"${compose[@]}" stop worker >/dev/null 2>&1 || true
cleanup

for _ in $(seq 1 60); do
  if curl --fail --silent --output /dev/null http://localhost:9090/actuator/health; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error --output /dev/null http://localhost:9090/actuator/health || {
  echo "API did not become healthy" >&2
  exit 1
}

database_name=$(docker exec jobdri-postgres \
  psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select current_database()')
if [[ "$database_name" != "jobdri_loadtest" ]]; then
  echo "refusing to drain a non-load-test database: $database_name" >&2
  exit 1
fi

task_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
  "select count(*), count(*) filter (where status = 'PENDING') from analysis_async_tasks")
IFS='|' read -r initial_task_count initial_pending_count <<< "$task_row"
queue_row=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers -q \
  | awk '$1 == "jobdri.analysis.execute" { print $2 "|" $3 "|" $4 }')
IFS='|' read -r initial_ready_count initial_unacked_count initial_consumer_count <<< "${queue_row:-0|0|0}"
initial_dlq_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q \
  | awk '$1 == "jobdri.analysis.execute.dlq" { print $2 }')
initial_dlq_count="${initial_dlq_count:-0}"

if [[ "$initial_task_count" != "$backlog_count" \
  || "$initial_pending_count" != "$backlog_count" \
  || "$initial_ready_count" != "$backlog_count" \
  || "$initial_unacked_count" != "0" \
  || "$initial_consumer_count" != "0" \
  || "$initial_dlq_count" != "0" ]]; then
  echo "invalid backlog snapshot: requested=$backlog_count tasks=$initial_task_count pending=$initial_pending_count ready=$initial_ready_count unacked=$initial_unacked_count consumers=$initial_consumer_count dlq=$initial_dlq_count" >&2
  exit 1
fi

started_millis=$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')
docker run -d --name "$worker_name" --network "$docker_network" \
  --env-file "$load_env_file" \
  -e WORKER_ENV=docker \
  -e RABBITMQ_HOST=rabbitmq \
  -e SPRING_API_BASE_URL=http://api:8080 \
  -e OPENAI_BASE_URL=http://ai-stub:18080/v1 \
  -e APP_WORKER_ANALYSIS_QUEUE_TIMEOUT_MILLIS="${LOAD_TEST_DRAIN_QUEUE_TIMEOUT_MILLIS:-3600000}" \
  -e WORKER_PREFETCH_COUNT="$worker_concurrency" \
  -e WORKER_ANALYSIS_CONCURRENCY_LIMIT="$worker_concurrency" \
  -e WORKER_DEFAULT_CONCURRENCY_LIMIT="$worker_concurrency" \
  "$worker_image" >/dev/null

completed=false
for _ in $(seq 1 "$timeout_seconds"); do
  terminal_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
    "select count(*) from analysis_async_tasks where status in ('SUCCEEDED', 'FAILED', 'CANCELLED')")
  queue_row=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged -q \
    | awk '$1 == "jobdri.analysis.execute" { print $2 "|" $3 }')
  IFS='|' read -r ready_count unacked_count <<< "${queue_row:-0|0}"
  if [[ "$terminal_count" == "$backlog_count" && "$ready_count" == "0" && "$unacked_count" == "0" ]]; then
    completed=true
    break
  fi
  sleep 1
done
finished_millis=$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')
wall_duration_millis=$((finished_millis - started_millis))

if [[ "$completed" != "true" ]]; then
  echo "drain timed out after ${timeout_seconds}s: terminal=${terminal_count:-0} ready=${ready_count:-0} unacked=${unacked_count:-0}" >&2
  docker logs --tail 100 "$worker_name" >&2
  exit 1
fi

status_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
  "select count(*) filter (where status = 'SUCCEEDED'), count(*) filter (where status = 'FAILED'), count(*) filter (where status = 'CANCELLED'), count(*) filter (where credit_status = 'CONFIRMED') from analysis_async_tasks")
IFS='|' read -r succeeded_count failed_count cancelled_count confirmed_count <<< "$status_row"
analysis_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select count(*) from analyses')
duplicate_analysis_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
  'select count(*) from (select mock_apply_id from analyses group by mock_apply_id having count(*) > 1) duplicates')
credit_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
  "select count(*) filter (where type = 'USE'), count(*) filter (where type = 'REFUND') from credit_transactions")
IFS='|' read -r use_count refund_count <<< "$credit_row"
duplicate_credit_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
  "select count(*) from (select user_id, reference_id from credit_transactions where type = 'USE' group by user_id, reference_id having count(*) > 1) duplicates")
dlq_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q \
  | awk '$1 == "jobdri.analysis.execute.dlq" { print $2 }')
dlq_count="${dlq_count:-0}"
timing_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
  "select round(extract(epoch from (max(completed_at) - min(started_at)))::numeric, 3), round(avg(queue_latency_millis)::numeric, 3), round(percentile_cont(0.95) within group (order by queue_latency_millis)::numeric, 3), round(avg(extract(epoch from (completed_at - started_at)))::numeric, 3), round(percentile_cont(0.95) within group (order by extract(epoch from (completed_at - started_at)))::numeric, 3) from analysis_async_tasks")
IFS='|' read -r processing_window_seconds queue_wait_avg_millis queue_wait_p95_millis completion_avg_seconds completion_p95_seconds <<< "$timing_row"

mkdir -p "$result_dir"
result_file="$result_dir/drain-${backlog_count}-c${worker_concurrency}.json"
python3 - "$result_file" "$backlog_count" "$worker_concurrency" "$wall_duration_millis" \
  "$processing_window_seconds" "$queue_wait_avg_millis" "$queue_wait_p95_millis" \
  "$completion_avg_seconds" "$completion_p95_seconds" <<'PY'
import json
import sys

path = sys.argv[1]
keys = (
    "backlogCount", "workerConcurrency", "wallDurationMillis", "processingWindowSeconds",
    "queueWaitAvgMillis", "queueWaitP95Millis", "completionAvgSeconds", "completionP95Seconds",
)
values = [int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), *map(float, sys.argv[5:])]
with open(path, "w", encoding="utf-8") as output:
    json.dump(dict(zip(keys, values)), output, indent=2)
    output.write("\n")
PY

printf 'scenario=queue_drain backlog=%s concurrency=%s succeeded=%s failed=%s cancelled=%s confirmed=%s analyses=%s use=%s refund=%s duplicate_analyses=%s duplicate_credit=%s dlq=%s wall_ms=%s processing_s=%s queue_wait_p95_ms=%s completion_p95_s=%s result=%s\n' \
  "$backlog_count" "$worker_concurrency" "$succeeded_count" "$failed_count" "$cancelled_count" \
  "$confirmed_count" "$analysis_count" "$use_count" "$refund_count" "$duplicate_analysis_count" \
  "$duplicate_credit_count" "$dlq_count" "$wall_duration_millis" "$processing_window_seconds" \
  "$queue_wait_p95_millis" "$completion_p95_seconds" "$result_file"

failed=0
assert_equal() {
  local field="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "assertion failed: $field expected=$expected actual=$actual" >&2
    failed=1
  fi
}

assert_equal succeeded "$backlog_count" "$succeeded_count"
assert_equal failed 0 "$failed_count"
assert_equal cancelled 0 "$cancelled_count"
assert_equal confirmed "$backlog_count" "$confirmed_count"
assert_equal analyses "$backlog_count" "$analysis_count"
assert_equal use_transactions "$backlog_count" "$use_count"
assert_equal refund_transactions 0 "$refund_count"
assert_equal duplicate_analyses 0 "$duplicate_analysis_count"
assert_equal duplicate_credit 0 "$duplicate_credit_count"
assert_equal dlq 0 "$dlq_count"

if (( failed != 0 )); then
  docker logs --tail 100 "$worker_name" >&2
  exit 1
fi
