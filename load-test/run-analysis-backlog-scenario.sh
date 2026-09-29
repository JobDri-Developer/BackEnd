#!/usr/bin/env bash
set -euo pipefail

backlog_count="${1:-}"
user_count="${2:-20}"
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
result_dir="${LOAD_TEST_RESULT_DIR:-load-test/results}"
compose=(docker compose --env-file "$load_env_file" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest)

if ! [[ "$backlog_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "usage: LOAD_TEST_ENV_FILE=/absolute/path/.env.loadtest $0 BACKLOG_COUNT [USER_COUNT]" >&2
  exit 2
fi
if ! [[ "$user_count" =~ ^[1-9][0-9]*$ ]] || (( user_count > backlog_count )); then
  echo "USER_COUNT must be a positive integer no greater than BACKLOG_COUNT" >&2
  exit 2
fi
if [[ ! -f "$load_env_file" ]]; then
  echo "load-test environment file not found: $load_env_file" >&2
  exit 2
fi

"${compose[@]}" up -d postgres redis rabbitmq api
"${compose[@]}" stop worker >/dev/null 2>&1 || true

for _ in $(seq 1 60); do
  if curl --silent --output /dev/null http://localhost:8080/actuator/health; then
    break
  fi
  sleep 1
done
curl --silent --show-error --output /dev/null http://localhost:8080/actuator/health || {
  echo "API did not become healthy" >&2
  exit 1
}

consumer_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name consumers -q \
  | awk '$1 == "jobdri.analysis.execute" { print $2 }')
if [[ "${consumer_count:-0}" != "0" ]]; then
  echo "refusing to create a backlog while jobdri.analysis.execute has active consumers: $consumer_count" >&2
  exit 1
fi

docker exec jobdri-rabbitmq rabbitmqctl purge_queue jobdri.analysis.execute >/dev/null 2>&1 || true
docker exec jobdri-rabbitmq rabbitmqctl purge_queue jobdri.analysis.execute.dlq >/dev/null 2>&1 || true
LOAD_TEST_ENV_FILE="$load_env_file" LOAD_TEST_RESULT_DIR="$result_dir" \
  bash load-test/seed/seed.sh "$backlog_count" "$user_count" >/dev/null

set -a
# shellcheck disable=SC1090
source "$result_dir/k6.env"
set +a

mkdir -p "$result_dir"
summary_file="/results/backlog-${backlog_count}.json"
"${compose[@]}" run --rm \
  -e BACKLOG_COUNT="$backlog_count" \
  -e SCENARIO="queue-backlog-${backlog_count}" \
  k6 run --summary-export="$summary_file" /scripts/analysis-backlog.js

task_row=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
  "select count(*), count(*) filter (where status = 'PENDING') from analysis_async_tasks")
IFS='|' read -r task_count pending_count <<< "$task_row"
analysis_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
  'select count(*) from analyses')
use_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
  "select count(*) from credit_transactions where type = 'USE'")
queue_row=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers -q \
  | awk '$1 == "jobdri.analysis.execute" { print $2 "|" $3 "|" $4 }')
IFS='|' read -r ready_count unacked_count final_consumer_count <<< "${queue_row:-0|0|0}"
dlq_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q \
  | awk '$1 == "jobdri.analysis.execute.dlq" { print $2 }')
dlq_count="${dlq_count:-0}"

printf 'scenario=queue_backlog requested=%s tasks=%s pending=%s queue_ready=%s unacked=%s consumers=%s dlq=%s analyses=%s use=%s summary=%s\n' \
  "$backlog_count" "$task_count" "$pending_count" "$ready_count" "$unacked_count" \
  "$final_consumer_count" "$dlq_count" "$analysis_count" "$use_count" \
  "$result_dir/backlog-${backlog_count}.json"

failed=0
assert_equal() {
  local field="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "assertion failed: $field expected=$expected actual=$actual" >&2
    failed=1
  fi
}

assert_equal tasks "$backlog_count" "$task_count"
assert_equal pending "$backlog_count" "$pending_count"
assert_equal queue_ready "$backlog_count" "$ready_count"
assert_equal unacked 0 "$unacked_count"
assert_equal consumers 0 "$final_consumer_count"
assert_equal dlq 0 "$dlq_count"
assert_equal analyses 0 "$analysis_count"
assert_equal use_transactions 0 "$use_count"

if (( failed != 0 )); then
  exit 1
fi
