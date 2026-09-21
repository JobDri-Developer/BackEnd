#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <input.csv> <output.csv>" >&2
  exit 2
fi

input_path=$1
output_path=$2

if [[ ! -f "$input_path" ]]; then
  echo "Input CSV not found: $input_path" >&2
  exit 2
fi
if [[ -e "$output_path" ]]; then
  echo "Output already exists; choose a new path: $output_path" >&2
  exit 2
fi
if [[ -z "${OPENAI_API_KEY:-}" || -z "${COHERE_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY and COHERE_API_KEY are required." >&2
  exit 2
fi
if [[ "${CONFIRM_FEWSHOT_CANARY_COST:-false}" != "true" ]]; then
  echo "Set CONFIRM_FEWSHOT_CANARY_COST=true after confirming external API cost." >&2
  exit 2
fi

mkdir -p "$(dirname "$output_path")"
export EVALUATION_ANALYSIS_ENABLED=true
export EVALUATION_INPUT="$input_path"
export EVALUATION_OUTPUT="$output_path"
export EVALUATION_CONFIRM_OPENAI_COST=true

exec ./gradlew bootRun --args='--spring.profiles.active=analysis-eval,fewshot-canary'
