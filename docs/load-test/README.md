# 공채 시즌 부하 검증 가이드

> 이 문서의 트래픽은 모두 `assumption`/`projected scenario`이다. 운영 실측값이 아니다.

## 1. 확인된 실행 경로

```text
POST /api/mock-applies/{id}/analysis
  -> AnalysisAsyncUseCase (사용자·입력·기존 결과·활성 task 검증)
  -> analysis_async_tasks PENDING 저장 (활성 user/mockApply DB unique 제약)
  -> AnalysisTaskMessagePublisher
  -> publisher confirm + persistent RabbitMQ message
  -> jobdri.analysis.execute
  -> [외부 worker: 이 저장소에 소스 없음]
  -> internal worker API (running/context/result/complete/retry/failure)
  -> mock_apply row lock -> LLM 결과 검증·정제 -> Analysis 교체 저장
  -> Credit RESERVED -> CONFIRMED, 실패/취소 시 RELEASED
  -> task 상태/SSE/notification
```

구성요소 위치:

| 책임 | 구현 |
|---|---|
| 요청 API | `AnalysisController` |
| 접수·중복 조율 | `AnalysisAsyncUseCase`, `AnalysisAsyncTaskService` |
| RabbitMQ producer/confirm | `AnalysisTaskMessagePublisher`, `RabbitPublishSupport` |
| exchange/queue/DLQ | `RabbitMqConfig`, `AnalysisQueueProperties` |
| consumer/concurrency | 별도 `analysis-server` 저장소의 `app/async_runtime.py`, `app/concurrency.py`; aio-pika prefetch와 task type별 limiter |
| OpenAI | Spring의 `OpenAiAnalysisAdapter`, worker의 `app/openai_client.py` |
| Cohere | `CohereEmbeddingClient` (3회 transient retry, exponential backoff) |
| retry/DLQ | Spring retry 콜백 + worker의 `app/async_runtime.py`; retry 재발행 후 원본 ack, exhausted/non-retryable은 DLQ publish confirm 후 ack |
| recovery spool | `worker_task_results` / `WorkerTaskResultService` (GENERATED/DELIVERED) |
| Credit | `AnalysisAsyncCreditCoordinator`, `AnalysisCreditService`, `CreditService` |
| 결과 검증/저장 | `AnalysisResultValidationService`, `AnalysisResultPersistenceService` |
| metrics | `AsyncMetricsRecorder`, Actuator `/actuator/prometheus` |
| 로컬 관측 | `ops/observability`, compose의 Prometheus/Loki/Alloy/Grafana |

## 2. 현재 안전장치와 병목 후보

- publisher confirm은 접수 응답 전에 최대 5초 기다린다. 브로커 지연 시 HTTP p95/p99가 직접 증가한다.
- 활성 분석 unique index와 `mock_apply` 비관적 lock이 동시 중복 저장을 직렬화한다. 다만 완료 시 기존 분석을 삭제·재생성하므로 서로 다른 task가 같은 지원서를 순차 완료하면 최신 callback이 이전 결과를 덮을 수 있다. task 단위 결과 idempotency만으로는 이를 완전히 막지 못한다.
- Credit 거래는 `(user_id,type,reference_id)` unique 제약과 user row lock으로 중복 차감을 방지한다. task Credit 상태 전이도 CONFIRMED에서 RELEASED로 가지 않는다.
- Spring 내부 LLM 경로는 blocking SDK + semaphore(기본 4)이다. concurrency 200은 안전한 시작값이 아니다. 외부 worker의 thread/connection 모델을 확인하기 전에는 `10 -> 25 -> 50` 단계와 prefetch 1을 권장한다.
- Cohere connection 상한은 전체 100/route 20이고 read timeout 기본 15초다. 50 이상 동시 요청에서 route pool과 DB pool을 함께 관찰한다.
- worker는 별도 `analysis-server` 저장소이므로 두 저장소를 함께 기동해야 end-to-end 검증할 수 있다. 실제 경로는 aio-pika consumer, AsyncOpenAI, httpx callback, 파일 spool이다.
- RabbitMQ queue depth/oldest age와 DLQ depth는 Spring metric이 아니라 RabbitMQ Prometheus plugin/exporter에서 수집해야 한다. 현재 compose에는 exporter 설정이 없다.

## 3. 시나리오

| 단계 | 입력 | 목적 | 기본 합격 기준 |
|---|---|---|---|
| A1 | 1 RPS, 10분 | 기준선 | 성공률 >=99%, p95 <1s |
| A2 | 10 RPS, 10분 | 지속 접수 | 성공률 >=99%, 발행 실패 <1% |
| A3 | 30 RPS, 2분 | 단기 burst | p99 <2s, 유실 0 |
| B1/B2 | consumer 정지 후 300/1,000건 접수 | 적체·drain | 아래 message/task 보존식 충족, 원인 없는 유실 0 |
| C | worker 10/50/(조건부 200) | 처리량 곡선 | 완료량·queue wait·외부 제한 비교 |
| D | stub 20/30/60초, 429, 500/502/503, timeout, invalid JSON/semantic, dimension mismatch | 장애 복구 | 정책대로 retry, 초과 시 DLQ, 검증 실패 저장 0 |
| E | 중복 callback, 재시작, 저장 전후 장애, spool replay, Credit 경계 | 불변식 | 아래 불변식 전부 충족 |

200 concurrency는 50에서 DB pool wait, Cohere route connection wait, 외부 API rate-limit, 메모리 및 callback 오류가 안정적일 때만 별도 비용 승인 후 수행한다.

## 4. 실행

기본 단위 테스트(외부 API/장시간 부하 없음):

```bash
./gradlew test
```

stub만 실행:

```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest up -d ai-stub
curl -H 'X-Stub-Mode: dimension_mismatch' -X POST http://localhost:18080/v2/embed -d '{"texts":["synthetic"],"output_dimension":3}'
```

합성 계정과 합성 mock apply ID만 준비한 뒤 접수 부하를 실행한다. 운영 주소/토큰을 넣지 않는다. `LOAD_TEST_ENV_FILE`에는 합성 자격 증명과 로컬 컨테이너 주소만 있는 별도 파일을 지정한다. 신규 분석 처리량 테스트는 iteration마다 서로 다른 ID를 소비하며 목록이 부족하면 즉시 실패한다. `constant-arrival-rate`는 duration 경계에서 iteration을 하나 더 예약할 수 있으므로 1% 여유를 둔다. 10 RPS 10분에는 최소 6,060개, 전체 projected 묶음에는 최소 10,206개의 ID를 준비한다.

격리 스택 기동 후 시드 스크립트를 실행하면 `jobdri_loadtest` DB인지 확인한 뒤 해당 DB의 데이터를 초기화하고 합성 ID와 JWT를 `load-test/results/k6.env`에 생성한다. 다른 DB 이름이면 아무것도 변경하지 않고 실패한다.

```bash
export LOAD_TEST_ENV_FILE='/absolute/path/to/.env.loadtest'
docker compose --env-file "$LOAD_TEST_ENV_FILE" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest up -d postgres redis rabbitmq ai-stub api prometheus
LOAD_TEST_ENV_FILE="$LOAD_TEST_ENV_FILE" bash load-test/seed/seed.sh 6060
```

복수 사용자 분산 시나리오는 두 번째 인자로 합성 사용자 수를 지정한다. 지원서는 사용자별로 round-robin 분배되고, `k6.env`에는 각 mock apply와 해당 JWT의 index 매핑이 생성된다. 두 번째 인자를 생략하면 기존 단일 사용자 시나리오로 동작한다.

```bash
LOAD_TEST_ENV_FILE="$LOAD_TEST_ENV_FILE" bash load-test/seed/seed.sh 6060 20
```

```bash
export LOAD_TEST_ENV_FILE='/absolute/path/to/.env.loadtest'
set -a
source load-test/results/k6.env
set +a

LOAD_TEST_TARGET_RPS=10 LOAD_TEST_DURATION=10m \
docker compose --env-file "$LOAD_TEST_ENV_FILE" -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest run --rm k6
```

A2를 접수 경로만 측정할 때는 worker를 중지하고 실행한다. end-to-end로 측정할 때는 별도 `analysis-server` worker의 `OPENAI_BASE_URL`을 stub으로 고정하고 worker concurrency/prefetch를 결과에 함께 기록한다. 실행 전 RabbitMQ의 analysis queue와 DLQ가 0인지 확인해 이전 실험 메시지가 결과에 섞이지 않게 한다.

30 RPS burst는 `LOAD_TEST_TARGET_RPS=30 LOAD_TEST_DURATION=2m`로 실행한다. 전체 projected 묶음을 호스트 k6로 실행할 때도 합성 환경 파일을 먼저 로드하고, `runAcceptance`가 읽는 변수명으로 명시적으로 매핑한다.

```bash
set -a
source "$LOAD_TEST_ENV_FILE"
set +a

BASE_URL="${LOAD_TEST_BASE_URL:-http://localhost:8080}" \
ACCESS_TOKEN="$LOAD_TEST_ACCESS_TOKEN" \
MOCK_APPLY_IDS="$LOAD_TEST_MOCK_APPLY_IDS" \
ACCESS_TOKENS="$LOAD_TEST_ACCESS_TOKENS" \
MOCK_APPLY_CASES="$LOAD_TEST_MOCK_APPLY_CASES" \
k6 run load-test/k6/projected-scenarios.js
```

active-task/cached-result 재사용은 신규 처리량과 섞지 않고 `analysis-duplicate.js`에서 하나의 합성 `MOCK_APPLY_ID`로 별도 검증한다. 결과는 `load-test/results/`에 생성되며 git에서 제외한다.

Queue 적체는 worker를 내린 격리 환경에서 A 스크립트로 정확히 300/1,000개의 서로 다른 합성 mock apply를 접수한다. 측정 구간 `I=[t0,t1]`과 종료 snapshot `T`를 고정하고 다음 두 보존식을 각각 확인한다.

```text
message 식 (messageId 기준, I 안에 publisher confirm 된 ID 집합 P):
|P| = |READY_T| + |UNACKED_T| + |ACKED_SUCCESS_I| + |DLQ_TERMINAL_I|

각 messageId는 우변에서 정확히 한 집합에만 속한다. DLQ publish 뒤 원본 ack가 발생한 ID는
ACKED_SUCCESS가 아니라 DLQ_TERMINAL로 분류한다. retry 발행이 새 messageId를 만들면 그 ID도 P에
포함하고, 같은 messageId를 재사용한다면 delivery attempt 수는 별도 counter로 기록한다.

task 식 (taskId 중복 제거, 동일 snapshot T):
|ACCEPTED_TASKS_{≤T}| = |PENDING_T| + |RUNNING_T| + |SUCCEEDED_T| + |FAILED_T| + |CANCELLED_T|

task 상태 집합은 상호 배타적이다. DLQ는 task 상태가 아니므로 우변에 더하지 않는다. DLQ message의
taskId는 FAILED_T taskId로 매핑되어야 하며, 매핑되지 않거나 한 task를 FAILED와 DLQ로 이중 계산하면 실패다.
```

## 5. 외부 API stub 모드

`X-Stub-Mode`: `success`, `429`, `500`, `502`, `503`, `invalid_json`, `semantic_invalid`, `dimension_mismatch`, `retry_then_success`.
`X-Stub-Latency-Seconds`: `20`, `30`, `60`. timeout은 client timeout보다 큰 latency로 재현한다. `X-Stub-Key`로 retry attempt를 묶는다. stub은 실제 API로 요청을 전달하지 않는다.

worker 장애·복구 경로는 합성 DB를 매 실행 초기화하는 아래 스크립트로 검증한다. `latency_20`, `latency_30`, `latency_60`은 각 지연 후 정상 완료와 Credit 확정을 확인하며 장시간 실행이므로 기본 테스트에 포함되지 않는다. `timeout`은 queue 만료 기준은 유지하고 worker의 독립 `OPENAI_TIMEOUT_SECONDS`만 1초, stub 지연을 2초로 단축해 재시도 소진 뒤 DLQ와 Credit 반환을 확인한다. `retry_then_success`는 OpenAI SDK 내부 재시도를 포함한 일곱 번째 stub 호출에서 성공시켜 worker 재시도 2회 뒤 복구되는지 확인하고, 영구 429/5xx는 재시도 소진 뒤 DLQ와 Credit 반환을 확인한다. `invalid_json`과 `semantic_invalid`는 재시도 없이 검증 실패로 종료되고 Analysis가 저장되지 않아야 한다. 이 스크립트는 로컬 격리 환경의 데이터를 초기화하므로 공유·운영 환경에서 실행하지 않는다.

```bash
export LOAD_TEST_ENV_FILE='/absolute/path/to/.env.loadtest'
./gradlew bootJar
docker compose --env-file "$LOAD_TEST_ENV_FILE" -f docker-compose.yml -f docker-compose.loadtest.yml build api

for mode in latency_20 latency_30 latency_60 timeout retry_then_success 429 503 invalid_json semantic_invalid; do
  bash load-test/run-analysis-failure-scenario.sh "$mode"
done
```

별도 worker 이미지 이름이나 compose network가 다르면 각각 `LOAD_TEST_WORKER_IMAGE`, `LOAD_TEST_DOCKER_NETWORK`로 지정한다. 영구 retryable 오류는 최초 시도와 3회 재시도 뒤 네 번째 실패에서 종료되므로 최종 task의 `retry_count`는 4다.

동일 메시지 재전달과 consumer 강제 종료 후 redelivery는 다음 스크립트로 검증한다. 두 시나리오는 결과 1개, USE 거래 1개, REFUND 0개, Credit CONFIRMED, queue/DLQ 0을 합격 조건으로 사용한다. `consumer_restart`는 기본 5초 stub latency 중 RUNNING 상태를 확인한 뒤 worker container를 강제 종료하고 새 worker를 시작하며, 새 consumer 로그의 RabbitMQ redelivery 표식도 확인한다.

```bash
bash load-test/run-analysis-idempotency-scenario.sh duplicate_delivery
bash load-test/run-analysis-idempotency-scenario.sh consumer_restart
bash load-test/run-analysis-idempotency-scenario.sh recovery_spool
```

`recovery_spool`은 load-test 전용 worker API proxy로 result 저장 요청은 통과시키고 complete callback만 일시적으로 503 처리한다. worker 결과가 GENERATED이고 spool 파일이 남은 것을 확인한 다음 worker를 종료하고, proxy를 정상화한 뒤 같은 spool volume으로 새 worker를 시작한다. replay 후에는 spool 0, worker 결과 DELIVERED, Analysis 1개, USE 거래 1개를 확인한다.

Spring 내부 OpenAI Java SDK base URL은 현재 설정에 노출되어 있지 않다. 주 분석 경로인 별도 worker에는 `OPENAI_BASE_URL=http://ai-stub:18080/v1`을 추가해 stub 연결이 가능하다. Cohere는 Spring에 `COHERE_BASE_URL=http://ai-stub:18080`을 지정한다.

## 6. 반드시 확인할 불변식

- task별 최종 결과 최대 1개. mock apply별로는 `analyses.mock_apply_id` unique 제약을 확인한다.
- 동일 message/callback 반복 후 USE 거래 1개, CONFIRMED 유지.
- terminal failure/cancel 후 REFUND 1개와 RELEASED.
- CONFIRMED는 RELEASED로 전이하지 않음.
- SUCCEEDED callback 반복은 저장된 결과를 바꾸지 않음.
- 구조/의미 검증 실패 응답은 Analysis에 저장되지 않음.
- DLQ/spool 레코드에 task ID, message ID, failure reason, retry count가 남음.

현재 테스트 중 `AnalysisWorkerBridgeServiceTest`, `AnalysisAsyncCreditCoordinatorTest`, `CreditServiceTest`, `AnalysisServiceTest`, `RabbitPublishSupportIntegrationTest`가 이 경계의 빠른 회귀 검증을 담당한다. RabbitMQ/PostgreSQL Testcontainers는 아직 없으므로 broker restart/consumer crash/실제 DLQ 검증은 격리 compose 환경의 수동 부하 단계다.

## 7. 측정 결과

2026-09-28 로컬 격리 환경에서 A2를 10 RPS, 10분 동안 실행했다. API와 PostgreSQL, Redis, RabbitMQ, AI stub을 Docker Desktop에서 실행했고, 별도 `analysis-server` worker는 prefetch 5 / analysis concurrency 5로 AI stub을 사용했다.

| 항목 | 측정값 |
|---|---:|
| 요청/iteration | 6,001 |
| 성공률 | 100% |
| 평균 | 17.37 ms |
| p90 / p95 | 23.40 ms / 35.31 ms |
| 최대 | 825.42 ms |
| dropped / interrupted | 0 / 0 |
| 종료 시 task | SUCCEEDED 6,001 |
| 종료 시 Analysis / USE 거래 | 6,001 / 6,001 |

`constant-arrival-rate` duration 경계에서 한 건이 추가 예약되어 6,001건이 실행됐다. 실행 전 DLQ를 비우지 않아 이전 실패 실험의 14건이 남아 있었으므로 이번 측정으로 DLQ=0 불변식은 판정하지 않는다. 다만 이번 실행에서 생성된 6,001개 task는 모두 SUCCEEDED였고 Analysis와 USE 거래 수도 각각 6,001개로 일치했다.

### C. worker concurrency drain

같은 로컬 격리 환경에서 worker를 중지하고 10 RPS로 20초간 200~201개 task를 적재한 뒤, 동시성별로 새 worker를 시작했다. 처리 구간은 DB의 `min(started_at)`부터 `max(completed_at)`까지이며 각 구간은 DB를 재시드하고 queue/DLQ를 비운 독립 실행이다.

| worker concurrency / prefetch | accepted | SUCCEEDED / FAILED | 처리 구간 | 처리량 | Hikari timeout |
|---:|---:|---:|---:|---:|---:|
| 5 | 201 | 201 / 0 | 3.342 s | 60.14 task/s | 0 |
| 10 | 201 | 201 / 0 | 2.641 s | 76.11 task/s | 0 |
| 25 | 200 | 200 / 0 | 3.544 s | 56.43 task/s | 0 |
| 50 | 201 | 201 / 0 | 5.710 s | 35.20 task/s | 0 |

이 합성 단일 계정 시나리오에서는 concurrency 10이 가장 높은 처리량을 보였고, 25와 50에서는 처리량이 감소했다. 로컬 기본값은 concurrency/prefetch 10으로 두고, 운영 결정 전에는 복수 사용자로 분산된 반복 측정과 실제 LLM rate limit을 함께 확인한다.

2026-09-29에는 같은 방법으로 20명에게 201개 task를 round-robin 분배해 재측정했다. 접수 구간의 VU는 100으로 선할당해 적재량을 고정했다.

| worker concurrency / prefetch | accepted | SUCCEEDED / FAILED | 처리 구간 | 처리량 | 접수 p95 | Hikari timeout |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 201 | 201 / 0 | 9.360 s | 21.47 task/s | 52.24 ms | 0 |
| 25 | 201 | 201 / 0 | 7.296 s | 27.55 task/s | 78.09 ms | 0 |
| 50 | 201 | 201 / 0 | 4.557 s | 44.11 task/s | 70.71 ms | 0 |

복수 사용자 합성 시나리오에서는 concurrency 50까지 처리량이 증가했고 모든 task가 실패 없이 완료됐다. 단일 실행과 AI stub을 사용한 결과이므로 운영 기본값은 유지하고, 반복 측정과 실제 LLM rate limit 검증 후에만 상향한다.

### D. worker 장애·복구

2026-09-29 로컬 격리 환경에서 단일 합성 task와 worker concurrency/prefetch 1로 실행했다. 각 모드는 DB와 queue/DLQ를 초기화한 독립 실행이다.

| stub mode | 최종 상태 | retry count | Credit | Analysis | DLQ |
|---|---|---:|---|---:|---:|
| `retry_then_success` (7번째 호출 성공) | SUCCEEDED | 2 | CONFIRMED | 1 | 0 |
| `429` | FAILED / RATE_LIMIT | 4 | RELEASED | 0 | 1 |
| `503` | FAILED / INTERNAL_ERROR | 4 | RELEASED | 0 | 1 |
| `invalid_json` | FAILED / VALIDATION_ERROR | 0 | RELEASED | 0 | 1 |
| `semantic_invalid` | FAILED / VALIDATION_ERROR | 0 | RELEASED | 0 | 1 |

영구 retryable 오류를 처음 측정했을 때 백엔드가 세 번째 retry callback에서 task를 조기 종료해 worker의 마지막 시도와 DLQ 발행을 건너뛰고 Credit이 RESERVED에 남는 정책 불일치를 발견했다. 백엔드의 종료 조건을 worker와 동일하게 `retryCount > maxRetryCount`로 맞춘 뒤 위 불변식이 모두 통과했다.

### E. 중복 전달·consumer 재시작

2026-09-29 로컬 격리 환경에서 동일한 RabbitMQ payload를 2개 적재한 중복 전달과, RUNNING 상태의 worker container를 강제 종료한 consumer 재시작을 각각 독립 실행했다.

| scenario | redelivery | 최종 상태 | Analysis | USE / REFUND | queue / DLQ |
|---|---|---|---:|---:|---:|
| duplicate delivery | 동일 message payload 2개 | SUCCEEDED / CONFIRMED | 1 | 1 / 0 | 0 / 0 |
| consumer restart | RabbitMQ `redelivered=true` | SUCCEEDED / CONFIRMED | 1 | 1 / 0 | 0 / 0 |
| recovery spool replay | `worker.recovery.replayed` | SUCCEEDED / CONFIRMED | 1 | 1 / 0 | 0 / 0 |

중복 메시지와 처리 중 강제 종료 모두 최종 결과와 Credit 차감을 중복 생성하지 않았다. recovery spool 시나리오는 complete callback 차단 중 `RUNNING / RESERVED`, worker 결과 `GENERATED`, spool 1개를 확인했다. 새 worker가 같은 spool을 replay한 뒤에는 worker 결과가 `DELIVERED`로 전이하고 spool이 0개가 됐으며, Analysis와 USE 거래는 각각 1개만 생성됐다.

## 8. 계측과 대시보드

추가/기존 주요 Prometheus 이름:

- `analysis_request_total{outcome="accepted|publish_failed"}`
- `analysis_job_total{event="completed|failed"}`
- `analysis_job_retry_total`, `analysis_job_duplicate_total`
- `analysis_credit_transition_total{transition="reserved|confirmed|released"}`
- `async_queue_wait_duration_seconds`, `async_processing_duration_seconds`
- `external_ai_request_duration_seconds{provider="openai"}`
- `llm_concurrency_*`, `hikaricp_connections_*`, `http_server_requests_seconds_*`
- worker: `worker_task_inflight`, `worker_task_concurrency_limit`, `worker_task_queue_wait_duration_seconds`
- worker: `worker_message_duplicate_total`, `worker_dlq_publish_total`, `worker_recovery_spool_pending`

현재 compose 이미지는 `rabbitmq:3.13-management-alpine`이며 별도 exporter가 아니라 RabbitMQ 3.13에 bundled된 `rabbitmq_prometheus` plugin을 사용한다. `docker-compose.loadtest.yml`을 함께 사용하면 load-test 전용 `enabled_plugins`가 plugin을 활성화하고, `prometheus-loadtest.yml`이 `rabbitmq:15692/metrics/per-object`를 15초 간격으로 scrape한다. 이 설정은 기본·운영 compose의 Prometheus 설정을 변경하지 않는다.

Prometheus target 상태는 `http://localhost:9090/targets` 또는 `/api/v1/targets`에서 `rabbitmq_queue` job이 `UP`인지 확인한다. 테스트 종료 후에는 동일 compose 파일로 기동한 서비스를 중지한다.

`/metrics/per-object`에서 확인할 이름은 `rabbitmq_queue_messages_ready`, `rabbitmq_queue_messages_unacked`, `rabbitmq_queue_consumers`, `rabbitmq_queue_messages_published_total`이다. delivery는 manual ack consumer인 현재 worker에서 `rabbitmq_channel_messages_delivered_ack_total`과 실제 ack 완료량 `rabbitmq_channel_messages_acked_total`을 사용한다. `rabbitmq_channel_messages_delivered_total`은 auto-ack delivery이므로 현재 worker consume rate로 사용하지 않는다. 고비용 per-object 전체 scrape 대신 `/metrics/detailed?family=queue_coarse_metrics&family=queue_consumer_count&family=channel_queue_metrics&family=channel_queue_exchange_metrics`를 쓰면 metric prefix가 `rabbitmq_detailed_`로 바뀐다. oldest message age는 `queue_metrics`의 `rabbitmq_detailed_queue_head_message_timestamp` 또는 worker의 `worker_task_queue_wait_duration_seconds` p99로 확인한다.

권장 alert:

- analysis queue ready > 300 for 10m (warning), > 1,000 for 5m (critical)
- queue wait p95 > 120s for 10m
- DLQ depth > 0 for 5m
- publish_failed > 0 또는 HTTP 5xx > 1% for 5m
- Credit transition failure > 0
- OpenAI/Cohere error > 5% 또는 p95 > client timeout의 80%
- Hikari active/max > 0.8 for 10m

## 9. 측정값 해석

시장 도달률과 0.24/1.8/8.8 RPS, 30 RPS burst는 용량 계획 가정이다. k6 summary, Prometheus snapshot, RabbitMQ Management API snapshot에서 얻은 값만 `measured`로 보고한다. 측정 전에는 결과 칸에 `not measured`를 적고 가정을 결과처럼 서술하지 않는다.
