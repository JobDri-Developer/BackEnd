#!/usr/bin/env bash
set -euo pipefail

backlog_count="${1:-}"
user_count="${2:-20}"
max_backlog_count=9007199254740991
load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
result_dir="${LOAD_TEST_RESULT_DIR:-load-test/results}"
compose=(docker compose --env-file "$load_env_file" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest)

if ! [[ "$backlog_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "usage: LOAD_TEST_ENV_FILE=/absolute/path/.env.loadtest $0 BACKLOG_COUNT [USER_COUNT]" >&2
  exit 2
fi
if (( 10#$backlog_count > max_backlog_count )); then
  echo "BACKLOG_COUNT must not exceed $max_backlog_count" >&2
  exit 2
fi
if ! [[ "$user_count" =~ ^[1-9][0-9]*$ ]] || (( 10#$user_count > 10#$backlog_count )); then
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
  if curl --fail --silent --output /dev/null http://localhost:9090/actuator/health; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error --output /dev/null http://localhost:9090/actuator/health || {
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

task_ids_file=$(mktemp)
trap 'rm -f "$task_ids_file"' EXIT
docker exec jobdri-postgres psql -U jobdri_loadtest -d jobdri_loadtest -Atc \
  'select task_id from analysis_async_tasks order by task_id' > "$task_ids_file"

python3 - "$load_env_file" "$backlog_count" "$task_ids_file" <<'PY'
import base64
import json
import sys
import urllib.parse
import urllib.request

env_path, expected_count_value, task_ids_path = sys.argv[1:]
expected_count = int(expected_count_value)
values = {}
with open(env_path, encoding="utf-8") as env_file:
    for raw_line in env_file:
        line = raw_line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value

username = values.get("RABBITMQ_USERNAME", "guest")
password = values.get("RABBITMQ_PASSWORD", "guest")
vhost = values.get("RABBITMQ_VHOST", "/")
queue = "jobdri.analysis.execute"
url = "http://localhost:15672/api/queues/{}/{}/get".format(
    urllib.parse.quote(vhost, safe=""),
    urllib.parse.quote(queue, safe=""),
)
request = urllib.request.Request(
    url,
    data=json.dumps({
        "count": expected_count,
        "ackmode": "ack_requeue_true",
        "encoding": "auto",
        "truncate": 100000,
    }).encode(),
    headers={
        "Authorization": "Basic " + base64.b64encode(f"{username}:{password}".encode()).decode(),
        "Content-Type": "application/json",
    },
    method="POST",
)
with urllib.request.urlopen(request, timeout=30) as response:
    messages = json.load(response)

with open(task_ids_path, encoding="utf-8") as task_file:
    database_task_ids = {line.strip() for line in task_file if line.strip()}

if len(messages) != expected_count:
    raise SystemExit(
        f"queue message count mismatch: expected={expected_count} observed={len(messages)}"
    )
if len(database_task_ids) != expected_count:
    raise SystemExit(
        f"database task count mismatch: expected={expected_count} observed={len(database_task_ids)}"
    )

message_ids = []
queue_task_ids = []
for index, queued in enumerate(messages):
    try:
        payload = json.loads(queued["payload"])
        message_id = payload["messageId"]
        task_id = payload["taskId"]
        property_message_id = queued["properties"]["message_id"]
    except (KeyError, TypeError, json.JSONDecodeError) as error:
        raise SystemExit(f"invalid queue message at index {index}: {error}") from error
    if not message_id or message_id != property_message_id:
        raise SystemExit(f"messageId property/payload mismatch at index {index}")
    message_ids.append(message_id)
    queue_task_ids.append(task_id)

duplicate_message_ids = len(message_ids) - len(set(message_ids))
duplicate_task_ids = len(queue_task_ids) - len(set(queue_task_ids))
missing_task_ids = database_task_ids - set(queue_task_ids)
unknown_task_ids = set(queue_task_ids) - database_task_ids
if duplicate_message_ids:
    raise SystemExit(f"duplicate queue messageIds: {duplicate_message_ids}")
if duplicate_task_ids:
    raise SystemExit(f"duplicate queue taskIds: {duplicate_task_ids}")
if missing_task_ids or unknown_task_ids:
    raise SystemExit(
        f"task/message mapping mismatch: missing={len(missing_task_ids)} unknown={len(unknown_task_ids)}"
    )

print(f"message_set={len(message_ids)} unique_message_ids={len(set(message_ids))} mapped_tasks={len(set(queue_task_ids))}")
PY

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
