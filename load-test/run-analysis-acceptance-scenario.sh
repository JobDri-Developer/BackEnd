#!/usr/bin/env bash
set -euo pipefail

target_rps="${1:-}"
duration="${2:-}"
user_count="${3:-20}"
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
result_dir="${LOAD_TEST_RESULT_DIR:-load-test/results}"
queue_timeout_seconds="${LOAD_TEST_ANALYSIS_QUEUE_TIMEOUT_SECONDS:-900}"
max_safe_integer=9007199254740991
compose=(docker compose --env-file "$load_env_file" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest)

if ! [[ "$target_rps" =~ ^[1-9][0-9]*$ ]]; then
  echo "usage: LOAD_TEST_ENV_FILE=/absolute/path/.env.loadtest $0 TARGET_RPS DURATION [USER_COUNT]" >&2
  exit 2
fi
if (( ${#target_rps} > 6 )) || (( 10#$target_rps > 100000 )); then
  echo "TARGET_RPS must not exceed 100000" >&2
  exit 2
fi
if ! [[ "$duration" =~ ^([1-9][0-9]*)(s|m|h)$ ]]; then
  echo "DURATION must be a positive integer followed by s, m, or h" >&2
  exit 2
fi
duration_value="${BASH_REMATCH[1]}"
duration_unit="${BASH_REMATCH[2]}"
if (( ${#duration_value} > 5 )); then
  echo "DURATION must not exceed 24 hours" >&2
  exit 2
fi
case "$duration_unit" in
  s) duration_seconds=$((10#$duration_value)) ;;
  m) duration_seconds=$((10#$duration_value * 60)) ;;
  h) duration_seconds=$((10#$duration_value * 3600)) ;;
esac
if (( duration_seconds > 86400 )); then
  echo "DURATION must not exceed 24 hours" >&2
  exit 2
fi
if ! [[ "$user_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "USER_COUNT must be a positive integer" >&2
  exit 2
fi
if (( ${#user_count} > 15 )); then
  echo "USER_COUNT is out of range" >&2
  exit 2
fi
if ! [[ "$queue_timeout_seconds" =~ ^[1-9][0-9]*$ ]] \
  || (( ${#queue_timeout_seconds} > 15 )) \
  || (( 10#$queue_timeout_seconds <= duration_seconds )); then
  echo "LOAD_TEST_ANALYSIS_QUEUE_TIMEOUT_SECONDS must be greater than the scenario duration (${duration_seconds}s)" >&2
  exit 2
fi
if [[ ! -f "$load_env_file" ]]; then
  echo "load-test environment file not found: $load_env_file" >&2
  exit 2
fi
if (( 10#$target_rps > max_safe_integer / duration_seconds )); then
  echo "TARGET_RPS multiplied by duration exceeds k6's safe-integer limit" >&2
  exit 2
fi

scheduled_count=$((10#$target_rps * duration_seconds))
fixture_headroom=$((scheduled_count / 100))
if (( fixture_headroom < 10 )); then
  fixture_headroom=10
fi
fixture_count=$((scheduled_count + fixture_headroom))
if (( 10#$user_count > fixture_count )); then
  echo "USER_COUNT must not exceed the generated fixture count ($fixture_count)" >&2
  exit 2
fi

export LOAD_TEST_ANALYSIS_QUEUE_TIMEOUT_SECONDS="$queue_timeout_seconds"
"${compose[@]}" up -d postgres redis rabbitmq api
"${compose[@]}" stop worker >/dev/null 2>&1 || true

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
  echo "refusing to seed non-load-test database: $database_name" >&2
  exit 1
fi

consumer_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name consumers -q \
  | awk '$1 == "jobdri.analysis.execute" { print $2 }')
if [[ "${consumer_count:-0}" != "0" ]]; then
  echo "refusing to run acceptance load while jobdri.analysis.execute has active consumers: $consumer_count" >&2
  exit 1
fi

docker exec jobdri-rabbitmq rabbitmqctl purge_queue jobdri.analysis.execute >/dev/null 2>&1 || true
docker exec jobdri-rabbitmq rabbitmqctl purge_queue jobdri.analysis.execute.dlq >/dev/null 2>&1 || true
LOAD_TEST_ENV_FILE="$load_env_file" LOAD_TEST_RESULT_DIR="$result_dir" \
  bash load-test/seed/seed.sh "$fixture_count" "$user_count" >/dev/null

set -a
# shellcheck disable=SC1090
source "$result_dir/k6.env"
set +a
export LOAD_TEST_TARGET_RPS="$target_rps"
export LOAD_TEST_DURATION="$duration"
export LOAD_TEST_SCENARIO="acceptance-${target_rps}rps-${duration}"

mkdir -p "$result_dir"
summary_name="acceptance-${target_rps}rps-${duration}.json"
"${compose[@]}" run --rm k6 run --summary-export="/results/$summary_name" /scripts/analysis-acceptance.js

accepted_count=$(python3 - "$result_dir/$summary_name" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as summary_file:
    summary = json.load(summary_file)
print(summary["metrics"]["iterations"]["count"])
PY
)

task_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
  "select count(*), count(distinct task_id), count(*) filter (where status = 'PENDING'), count(*) filter (where status = 'FAILED') from analysis_async_tasks")
IFS='|' read -r task_count distinct_task_count pending_count failed_count <<< "$task_row"
analysis_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select count(*) from analyses')
credit_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select count(*) from credit_transactions')
queue_row=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers -q \
  | awk '$1 == "jobdri.analysis.execute" { print $2 "|" $3 "|" $4 }')
IFS='|' read -r ready_count unacked_count final_consumer_count <<< "${queue_row:-0|0|0}"
dlq_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q \
  | awk '$1 == "jobdri.analysis.execute.dlq" { print $2 }')
dlq_count="${dlq_count:-0}"

failed=0
assert_equal() {
  local field="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "assertion failed: $field expected=$expected actual=$actual" >&2
    failed=1
  fi
}

assert_equal tasks "$accepted_count" "$task_count"
assert_equal distinct_tasks "$accepted_count" "$distinct_task_count"
assert_equal pending "$accepted_count" "$pending_count"
assert_equal failed 0 "$failed_count"
assert_equal queue_ready "$accepted_count" "$ready_count"
assert_equal unacked 0 "$unacked_count"
assert_equal consumers 0 "$final_consumer_count"
assert_equal dlq 0 "$dlq_count"
assert_equal analyses 0 "$analysis_count"
assert_equal credit_transactions 0 "$credit_count"

printf 'scenario=acceptance target_rps=%s duration=%s accepted=%s tasks=%s pending=%s queue_ready=%s unacked=%s consumers=%s dlq=%s analyses=%s credit_transactions=%s summary=%s\n' \
  "$target_rps" "$duration" "$accepted_count" "$task_count" "$pending_count" "$ready_count" \
  "$unacked_count" "$final_consumer_count" "$dlq_count" "$analysis_count" "$credit_count" \
  "$result_dir/$summary_name"

if (( failed != 0 )); then
  exit 1
fi
