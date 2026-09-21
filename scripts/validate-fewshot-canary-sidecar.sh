#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -r "$1" ]]; then
  echo "Usage: $0 <evaluation-output.csv.fewshot.RUN_ID.jsonl>" >&2
  exit 2
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 2
fi

sidecar_path=$1
allowed_ids='["FS-02","FS-03","FS-05","FS-08","FS-09"]'

jq --exit-status --slurp --argjson allowed "$allowed_ids" '
  length > 0
  and all(.[]; .outcome == "SUCCESS" and .metadataStatus == "RECORDED")
  and all(.[]?.selections[]?; .selectionMode == "EMBEDDING" or .selectionMode == "LOCAL_FALLBACK")
  and all(.[]?.selections[]?.selectedCases[]?;
    .id as $id | .source == "REVIEWED_PRODUCTION" and ($allowed | index($id)) != null)
' "$sidecar_path" >/dev/null

echo "Few-shot canary sidecar validation passed: $sidecar_path"
