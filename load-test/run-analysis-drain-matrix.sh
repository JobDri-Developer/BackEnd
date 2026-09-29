#!/usr/bin/env bash
set -euo pipefail

load_env_file="${LOAD_TEST_ENV_FILE:?Set LOAD_TEST_ENV_FILE to the synthetic-only environment file}"
backlog_counts="${LOAD_TEST_MATRIX_BACKLOG_COUNTS:-300}"
worker_concurrencies="${LOAD_TEST_MATRIX_CONCURRENCIES:-10,25,50}"
repeat_count="${LOAD_TEST_MATRIX_REPEAT_COUNT:-3}"
user_count="${LOAD_TEST_MATRIX_USER_COUNT:-20}"
allow_high_resource="${LOAD_TEST_ALLOW_HIGH_RESOURCE:-false}"
dry_run="${LOAD_TEST_MATRIX_DRY_RUN:-false}"
result_root="${LOAD_TEST_RESULT_DIR:-load-test/results}"
run_id="${LOAD_TEST_MATRIX_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
run_dir="$result_root/drain-matrix/$run_id"

if [[ ! -f "$load_env_file" ]]; then
  echo "load-test environment file not found: $load_env_file" >&2
  exit 2
fi
if ! [[ "$repeat_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "LOAD_TEST_MATRIX_REPEAT_COUNT must be a positive integer" >&2
  exit 2
fi
if ! [[ "$user_count" =~ ^[1-9][0-9]*$ ]]; then
  echo "LOAD_TEST_MATRIX_USER_COUNT must be a positive integer" >&2
  exit 2
fi
if [[ "$allow_high_resource" != "true" && "$allow_high_resource" != "false" ]]; then
  echo "LOAD_TEST_ALLOW_HIGH_RESOURCE must be true or false" >&2
  exit 2
fi
if [[ "$dry_run" != "true" && "$dry_run" != "false" ]]; then
  echo "LOAD_TEST_MATRIX_DRY_RUN must be true or false" >&2
  exit 2
fi
if ! [[ "$run_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "LOAD_TEST_MATRIX_RUN_ID may contain only letters, digits, dot, underscore, and hyphen" >&2
  exit 2
fi

IFS=',' read -r -a backlog_values <<< "$backlog_counts"
IFS=',' read -r -a concurrency_values <<< "$worker_concurrencies"
for backlog_count in "${backlog_values[@]}"; do
  if ! [[ "$backlog_count" =~ ^[1-9][0-9]*$ ]]; then
    echo "invalid backlog count: $backlog_count" >&2
    exit 2
  fi
  if (( 10#$user_count > 10#$backlog_count )); then
    echo "LOAD_TEST_MATRIX_USER_COUNT must not exceed backlog count $backlog_count" >&2
    exit 2
  fi
done
for worker_concurrency in "${concurrency_values[@]}"; do
  if ! [[ "$worker_concurrency" =~ ^[1-9][0-9]*$ ]]; then
    echo "invalid worker concurrency: $worker_concurrency" >&2
    exit 2
  fi
done

execution_count=$((${#backlog_values[@]} * ${#concurrency_values[@]} * 10#$repeat_count))
printf 'drain matrix: backlogs=%s concurrencies=%s repeats=%s executions=%s dry_run=%s\n' \
  "$backlog_counts" "$worker_concurrencies" "$repeat_count" "$execution_count" "$dry_run"

for backlog_count in "${backlog_values[@]}"; do
  for worker_concurrency in "${concurrency_values[@]}"; do
    if (( 10#$backlog_count >= 1000 || 10#$worker_concurrency > 50 )) \
      && [[ "$allow_high_resource" != "true" ]]; then
      echo "backlog $backlog_count / concurrency $worker_concurrency requires LOAD_TEST_ALLOW_HIGH_RESOURCE=true" >&2
      exit 2
    fi
    for repeat in $(seq 1 "$repeat_count"); do
      printf 'matrix run: backlog=%s concurrency=%s repeat=%s/%s\n' \
        "$backlog_count" "$worker_concurrency" "$repeat" "$repeat_count"
      if [[ "$dry_run" == "true" ]]; then
        continue
      fi

      LOAD_TEST_ENV_FILE="$load_env_file" \
        bash load-test/run-analysis-backlog-scenario.sh "$backlog_count" "$user_count"
      LOAD_TEST_ENV_FILE="$load_env_file" \
        bash load-test/run-analysis-drain-scenario.sh "$backlog_count" "$worker_concurrency"

      mkdir -p "$run_dir"
      drain_source="$result_root/drain-${backlog_count}-c${worker_concurrency}.json"
      backlog_source="$result_root/backlog-${backlog_count}.json"
      drain_target="$run_dir/drain-${backlog_count}-c${worker_concurrency}-r${repeat}.json"
      backlog_target="$run_dir/backlog-${backlog_count}-c${worker_concurrency}-r${repeat}.json"
      if [[ ! -f "$drain_source" || ! -f "$backlog_source" ]]; then
        echo "missing scenario result for backlog=$backlog_count concurrency=$worker_concurrency repeat=$repeat" >&2
        exit 1
      fi
      cp "$drain_source" "$drain_target"
      cp "$backlog_source" "$backlog_target"
    done
  done
done

if [[ "$dry_run" == "true" ]]; then
  echo "dry run complete; no load was generated"
  exit 0
fi

python3 - "$run_dir" <<'PY'
import csv
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

run_dir = Path(sys.argv[1])
pattern = re.compile(r"drain-(\d+)-c(\d+)-r(\d+)\.json$")
runs = []
for path in sorted(run_dir.glob("drain-*.json")):
    match = pattern.fullmatch(path.name)
    if not match:
        continue
    with path.open(encoding="utf-8") as source:
        result = json.load(source)
    result["repeat"] = int(match.group(3))
    result["throughputPerSecond"] = result["backlogCount"] / result["processingWindowSeconds"]
    result["source"] = path.name
    runs.append(result)

groups = defaultdict(list)
for run in runs:
    groups[(run["backlogCount"], run["workerConcurrency"])].append(run)

metrics = (
    "wallDurationMillis",
    "processingWindowSeconds",
    "queueWaitP95Millis",
    "completionP95Seconds",
    "throughputPerSecond",
)
summaries = []
for (backlog_count, concurrency), group_runs in sorted(groups.items()):
    summary = {
        "backlogCount": backlog_count,
        "workerConcurrency": concurrency,
        "repeats": len(group_runs),
    }
    for metric in metrics:
        values = [float(run[metric]) for run in group_runs]
        summary[metric] = {
            "mean": statistics.fmean(values),
            "median": statistics.median(values),
            "min": min(values),
            "max": max(values),
        }
    summaries.append(summary)

with (run_dir / "summary.json").open("w", encoding="utf-8") as output:
    json.dump({"runs": runs, "groups": summaries}, output, indent=2)
    output.write("\n")

with (run_dir / "summary.csv").open("w", encoding="utf-8", newline="") as output:
    fieldnames = ["backlogCount", "workerConcurrency", "repeats"]
    fieldnames += [f"{metric}_{stat}" for metric in metrics for stat in ("mean", "median", "min", "max")]
    writer = csv.DictWriter(output, fieldnames=fieldnames)
    writer.writeheader()
    for summary in summaries:
        row = {key: summary[key] for key in ("backlogCount", "workerConcurrency", "repeats")}
        for metric in metrics:
            for stat in ("mean", "median", "min", "max"):
                row[f"{metric}_{stat}"] = summary[metric][stat]
        writer.writerow(row)
PY

echo "matrix results: $run_dir/summary.json"
echo "matrix table: $run_dir/summary.csv"
