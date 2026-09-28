# 부하 검증 결과 — YYYY-MM-DD

환경/commit:  
실행자:  
데이터: synthetic only  
결과 상태: `measured` / `not measured`

| scenario | input rate | burst rate | worker concurrency | mock latency | duration | accepted | success rate | HTTP p50/p95/p99 | queue depth max | queue wait p95 | completion p95 | throughput | retry | DLQ | duplicates | Credit invariant | DB pool max | CPU/memory | result source |
|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|---|
| assumption-initial | 0.24/s | 0.72/s | TBD | 0s | TBD | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | assumption |
| assumption-growth | 1.8/s | 5.4/s | TBD | 0s | TBD | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | assumption |
| assumption-recruiting-peak | 8.8/s | 26.4/s | TBD | 0s | TBD | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | not measured | assumption |

## 불변식 판정

| invariant | evidence query/test | result |
|---|---|---|
| task당 최종 결과 최대 1개 |  | not measured |
| 중복 delivery에도 Credit confirm 1회 |  | not measured |
| 실패 Credit 반환 |  | not measured |
| CONFIRMED -> RELEASED 없음 |  | not measured |
| 완료 결과 재시도 덮어쓰기 없음 |  | not measured |
| validation 실패 결과 저장 없음 |  | not measured |
| DLQ/spool 원인·task ID 추적 가능 |  | not measured |

## 병목과 결정

- 최초 포화 지점:
- 안전 worker concurrency:
- DB/RabbitMQ/external API 근거:
- 다음 조치와 담당자:
