#!/usr/bin/env bash
set -euo pipefail

scenario="${1:-}"
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
worker_image="${LOAD_TEST_WORKER_IMAGE:-jobdri-analysis-worker-loadtest:latest}"
docker_network="${LOAD_TEST_DOCKER_NETWORK:-backend_default}"
worker_name="${LOAD_TEST_WORKER_NAME:-jobdri-analysis-worker-idempotency-test}"
timeout_seconds="${LOAD_TEST_SCENARIO_TIMEOUT_SECONDS:-120}"
compose=(docker compose --env-file "$load_env_file" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest)
worker_api_base_url=http://api:8080

case "$scenario" in
  duplicate_delivery)
    stub_latency_seconds=0
    ;;
  consumer_restart)
    stub_latency_seconds="${LOAD_TEST_RESTART_STUB_LATENCY_SECONDS:-5}"
    ;;
  recovery_spool)
    stub_latency_seconds=0
    worker_api_base_url=http://worker-api-proxy:18081
    spool_dir=$(mktemp -d)
    chmod 0777 "$spool_dir"
    ;;
  *)
    echo "usage: LOAD_TEST_ENV_FILE=/absolute/path/.env.loadtest $0 {duplicate_delivery|consumer_restart|recovery_spool}" >&2
    exit 2
    ;;
esac

if ! [[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
  echo "LOAD_TEST_SCENARIO_TIMEOUT_SECONDS must be a positive integer" >&2
  exit 2
fi
if ! [[ "$stub_latency_seconds" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "stub latency must be a non-negative number" >&2
  exit 2
fi
if [[ ! -f "$load_env_file" ]]; then
  echo "load-test environment file not found: $load_env_file" >&2
  exit 2
fi

cleanup() {
  docker rm -f "$worker_name" >/dev/null 2>&1 || true
  if [[ -n "${message_file:-}" ]]; then
    rm -f "$message_file"
  fi
  if [[ -n "${spool_dir:-}" && -d "$spool_dir" ]]; then
    find "$spool_dir" -mindepth 1 -delete
    rmdir "$spool_dir"
  fi
}
trap cleanup EXIT

start_worker() {
  set -- docker run -d --name "$worker_name" --network "$docker_network" \
    --env-file "$load_env_file" \
    -e WORKER_ENV=docker \
    -e RABBITMQ_HOST=rabbitmq \
    -e SPRING_API_BASE_URL="$worker_api_base_url" \
    -e OPENAI_BASE_URL=http://ai-stub:18080/v1 \
    -e WORKER_PREFETCH_COUNT=1 \
    -e WORKER_ANALYSIS_CONCURRENCY_LIMIT=1 \
    -e WORKER_DEFAULT_CONCURRENCY_LIMIT=1 \
    -e APP_WORKER_API_RETRY_MAX_ATTEMPTS=2 \
    -e APP_WORKER_API_RETRY_BASE_DELAY_MILLIS=100 \
    -e APP_WORKER_API_RETRY_MAX_DELAY_MILLIS=100 \
    -e APP_WORKER_RECOVERY_POLL_INTERVAL_SECONDS=1
  if [[ -n "${spool_dir:-}" ]]; then
    set -- "$@" -v "$spool_dir:/app/.worker-spool"
  fi
  set -- "$@" "$worker_image"
  "$@" >/dev/null
}

task_snapshot() {
  docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -AtF '|' -c \
    "select status, retry_count, credit_status, coalesce(failure_reason::text, '') from analysis_async_tasks order by created_at desc limit 1"
}

wait_for_task_status() {
  local expected_status="$1" snapshot=""
  for _ in $(seq 1 "$timeout_seconds"); do
    snapshot=$(task_snapshot)
    if [[ "$snapshot" == "$expected_status"\|* ]]; then
      printf '%s\n' "$snapshot"
      return 0
    fi
    sleep 1
  done
  echo "task did not reach $expected_status; last snapshot=$snapshot" >&2
  return 1
}

export LOAD_TEST_STUB_MODE=success
export LOAD_TEST_STUB_LATENCY_SECONDS="$stub_latency_seconds"
"${compose[@]}" up -d postgres redis rabbitmq api
"${compose[@]}" up -d --build --no-deps --force-recreate ai-stub
if [[ "$scenario" == "recovery_spool" ]]; then
  export LOAD_TEST_PROXY_FAIL_COMPLETE=true
  "${compose[@]}" up -d --build --no-deps --force-recreate worker-api-proxy
fi
"${compose[@]}" stop worker >/dev/null 2>&1 || true

for _ in $(seq 1 60); do
  if curl --silent --output /dev/null http://localhost:8080/actuator/health; then
    break
  fi
  sleep 1
done
curl --silent --output /dev/null http://localhost:8080/actuator/health || {
  echo "API did not become reachable" >&2
  exit 1
}

docker rm -f "$worker_name" >/dev/null 2>&1 || true
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

if [[ "$scenario" == "duplicate_delivery" ]]; then
  message_file=$(mktemp)
  docker exec jobdri-rabbitmq sh -c \
    'rabbitmqadmin -u "$RABBITMQ_DEFAULT_USER" -p "$RABBITMQ_DEFAULT_PASS" get queue=jobdri.analysis.execute ackmode=ack_requeue_true payload_file=/tmp/jobdri-analysis-message.json' \
    >/dev/null
  docker cp jobdri-rabbitmq:/tmp/jobdri-analysis-message.json "$message_file" >/dev/null
  docker exec -i jobdri-rabbitmq sh -c \
    'rabbitmqadmin -u "$RABBITMQ_DEFAULT_USER" -p "$RABBITMQ_DEFAULT_PASS" publish exchange=jobdri.worker.exchange routing_key=analysis.execute properties='"'"'{"delivery_mode":2}'"'"'' \
    < "$message_file" >/dev/null

  queued_messages=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q | awk '$1 == "jobdri.analysis.execute" { print $2 }')
  if [[ "$queued_messages" != "2" ]]; then
    echo "expected two queued copies before worker start; actual=${queued_messages:-0}" >&2
    exit 1
  fi

  start_worker
elif [[ "$scenario" == "consumer_restart" ]]; then
  start_worker
  wait_for_task_status RUNNING >/dev/null
  docker kill "$worker_name" >/dev/null
  docker rm "$worker_name" >/dev/null
  start_worker
else
  start_worker
  deferred_observed=false
  for _ in $(seq 1 "$timeout_seconds"); do
    result_status=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
      "select status from worker_task_results order by created_at desc limit 1")
    worker_logs=$(docker logs "$worker_name" 2>&1)
    spool_count=$(find "$spool_dir" -maxdepth 1 -name 'analysis_complete-*.json' | wc -l | tr -d ' ')
    if [[ "$result_status" == "GENERATED" && "$spool_count" == "1" ]] \
      && grep -q '"event": "worker.delivery.deferred"' <<< "$worker_logs"; then
      deferred_observed=true
      break
    fi
    sleep 1
  done
  if [[ "$deferred_observed" != "true" ]]; then
    echo "recovery spool was not deferred; result_status=${result_status:-missing} spool_count=${spool_count:-0}" >&2
    exit 1
  fi

  before_replay=$(task_snapshot)
  if [[ "$before_replay" != RUNNING\|*\|RESERVED\|* ]]; then
    echo "unexpected task state before recovery replay: $before_replay" >&2
    exit 1
  fi

  docker rm -f "$worker_name" >/dev/null
  export LOAD_TEST_PROXY_FAIL_COMPLETE=false
  "${compose[@]}" up -d --no-deps --force-recreate worker-api-proxy >/dev/null
  start_worker
fi

task_row=$(wait_for_task_status SUCCEEDED)
IFS='|' read -r actual_status actual_retry_count actual_credit_status actual_failure_reason <<< "$task_row"
analysis_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc 'select count(*) from analyses')
use_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc "select count(*) from credit_transactions where type = 'USE'")
refund_count=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc "select count(*) from credit_transactions where type = 'REFUND'")
queue_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q | awk '$1 == "jobdri.analysis.execute" { print $2 }')
dlq_count=$(docker exec jobdri-rabbitmq rabbitmqctl list_queues name messages -q | awk '$1 == "jobdri.analysis.execute.dlq" { print $2 }')
queue_count="${queue_count:-0}"
dlq_count="${dlq_count:-0}"
redelivery_observed=not_applicable
recovery_replayed=not_applicable
if [[ "$scenario" == "consumer_restart" ]]; then
  worker_logs=$(docker logs "$worker_name" 2>&1)
  if grep -q '"redelivered": true' <<< "$worker_logs"; then
    redelivery_observed=true
  else
    redelivery_observed=false
  fi
fi
if [[ "$scenario" == "recovery_spool" ]]; then
  worker_logs=$(docker logs "$worker_name" 2>&1)
  if grep -q '"event": "worker.recovery.replayed"' <<< "$worker_logs"; then
    recovery_replayed=true
  else
    recovery_replayed=false
  fi
  remaining_spool_count=$(find "$spool_dir" -maxdepth 1 -name 'analysis_complete-*.json' | wc -l | tr -d ' ')
  result_status=$(docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
    "select status from worker_task_results order by created_at desc limit 1")
else
  remaining_spool_count=not_applicable
  result_status=not_applicable
fi

printf 'scenario=%s status=%s retry_count=%s credit_status=%s analyses=%s use=%s refund=%s queue=%s dlq=%s redelivery=%s recovery_replayed=%s spool=%s result_status=%s\n' \
  "$scenario" "$actual_status" "$actual_retry_count" "$actual_credit_status" \
  "$analysis_count" "$use_count" "$refund_count" "$queue_count" "$dlq_count" "$redelivery_observed" \
  "$recovery_replayed" "$remaining_spool_count" "$result_status"

failed=0
assert_equal() {
  local field="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "assertion failed: $field expected=$expected actual=$actual" >&2
    failed=1
  fi
}
assert_equal status SUCCEEDED "$actual_status"
assert_equal credit_status CONFIRMED "$actual_credit_status"
assert_equal failure_reason "" "$actual_failure_reason"
assert_equal analyses 1 "$analysis_count"
assert_equal use_transactions 1 "$use_count"
assert_equal refund_transactions 0 "$refund_count"
assert_equal queue 0 "$queue_count"
assert_equal dlq 0 "$dlq_count"
if [[ "$scenario" == "consumer_restart" ]]; then
  assert_equal redelivery true "$redelivery_observed"
fi
if [[ "$scenario" == "recovery_spool" ]]; then
  assert_equal recovery_replayed true "$recovery_replayed"
  assert_equal remaining_spool 0 "$remaining_spool_count"
  assert_equal worker_result_status DELIVERED "$result_status"
fi

if (( failed != 0 )); then
  echo "worker logs:" >&2
  docker logs --tail 100 "$worker_name" >&2
  exit 1
fi
