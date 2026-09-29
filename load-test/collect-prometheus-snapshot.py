#!/usr/bin/env python3
import json
import sys
import urllib.parse
import urllib.request


if len(sys.argv) != 4:
    raise SystemExit("usage: collect-prometheus-snapshot.py BASE_URL START_SECONDS END_SECONDS")

base_url, start_seconds, end_seconds = sys.argv[1:]
queries = {
    "workerInflight": 'worker_task_inflight{task_type="analysis"}',
    "workerSaturationPercent": '100 * worker_task_inflight{task_type="analysis"} / clamp_min(worker_task_concurrency_limit{task_type="analysis"}, 1)',
    "dbActiveConnections": "hikaricp_connections_active",
    "dbPendingConnections": "hikaricp_connections_pending",
    "dbPoolUtilizationPercent": "100 * hikaricp_connections_active / clamp_min(hikaricp_connections_max, 1)",
    "queueReady": 'rabbitmq_queue_messages_ready{queue="jobdri.analysis.execute"}',
    "hostCpuPercent": '100 * (1 - avg(rate(node_cpu_seconds_total{mode="idle"}[5s])))',
    "hostMemoryPercent": "100 * (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)",
}


def query_max(expression):
    params = urllib.parse.urlencode({
        "query": expression,
        "start": start_seconds,
        "end": end_seconds,
        "step": "1s",
    })
    with urllib.request.urlopen(f"{base_url}/api/v1/query_range?{params}", timeout=10) as response:
        payload = json.load(response)
    if payload.get("status") != "success":
        raise RuntimeError(f"Prometheus query failed: {expression}")
    values = [
        float(sample[1])
        for series in payload["data"]["result"]
        for sample in series.get("values", [])
        if sample[1] not in ("NaN", "+Inf", "-Inf")
    ]
    return max(values) if values else None


metrics = {name: query_max(expression) for name, expression in queries.items()}
print(json.dumps({
    "max": metrics,
    "missing": [name for name, value in metrics.items() if value is None],
}))
