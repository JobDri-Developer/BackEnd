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

합성 계정과 합성 mock apply ID만 준비한 뒤 접수 부하를 실행한다. 운영 주소/토큰을 넣지 않는다. 신규 분석 처리량 테스트는 iteration마다 서로 다른 ID를 소비하며 목록이 부족하면 즉시 실패한다. 10 RPS 10분에는 최소 6,000개, 전체 projected 묶음에는 최소 10,104개의 ID가 필요하다.

```bash
LOAD_TEST_ACCESS_TOKEN='test-token' \
LOAD_TEST_MOCK_APPLY_IDS='<6,000개의 서로 다른 합성 ID를 쉼표로 연결>' \
LOAD_TEST_TARGET_RPS=10 LOAD_TEST_DURATION=10m \
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml --profile loadtest run --rm k6
```

30 RPS burst는 `LOAD_TEST_TARGET_RPS=30 LOAD_TEST_DURATION=2m`로 실행한다. 전체 projected 묶음은 로컬 k6에서 `k6 run load-test/k6/projected-scenarios.js`로 명시 실행한다. active-task/cached-result 재사용은 신규 처리량과 섞지 않고 `analysis-duplicate.js`에서 하나의 합성 `MOCK_APPLY_ID`로 별도 검증한다. 결과는 `load-test/results/`에 생성되며 git에서 제외한다.

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

## 7. 계측과 대시보드

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

현재 compose 이미지는 `rabbitmq:3.13-management-alpine`이며 별도 exporter가 아니라 RabbitMQ 3.13에 bundled된 `rabbitmq_prometheus` plugin을 사용한다. 현재 compose에는 이 plugin과 15692 scrape가 설정되어 있지 않으므로 측정 전에 격리 환경에서 `rabbitmq-plugins enable rabbitmq_prometheus`로 활성화해야 한다. 기본 endpoint는 `rabbitmq:15692/metrics`(집계)이고, queue label이 필요한 이 테스트는 `rabbitmq:15692/metrics/per-object`를 15초 간격으로 scrape한다.

`/metrics/per-object`에서 확인할 이름은 `rabbitmq_queue_messages_ready`, `rabbitmq_queue_messages_unacked`, `rabbitmq_queue_consumers`, `rabbitmq_queue_messages_published_total`이다. delivery는 manual ack consumer인 현재 worker에서 `rabbitmq_channel_messages_delivered_ack_total`과 실제 ack 완료량 `rabbitmq_channel_messages_acked_total`을 사용한다. `rabbitmq_channel_messages_delivered_total`은 auto-ack delivery이므로 현재 worker consume rate로 사용하지 않는다. 고비용 per-object 전체 scrape 대신 `/metrics/detailed?family=queue_coarse_metrics&family=queue_consumer_count&family=channel_queue_metrics&family=channel_queue_exchange_metrics`를 쓰면 metric prefix가 `rabbitmq_detailed_`로 바뀐다. oldest message age는 `queue_metrics`의 `rabbitmq_detailed_queue_head_message_timestamp` 또는 worker의 `worker_task_queue_wait_duration_seconds` p99로 확인한다.

권장 alert:

- analysis queue ready > 300 for 10m (warning), > 1,000 for 5m (critical)
- queue wait p95 > 120s for 10m
- DLQ depth > 0 for 5m
- publish_failed > 0 또는 HTTP 5xx > 1% for 5m
- Credit transition failure > 0
- OpenAI/Cohere error > 5% 또는 p95 > client timeout의 80%
- Hikari active/max > 0.8 for 10m

## 8. 측정값 해석

시장 도달률과 0.24/1.8/8.8 RPS, 30 RPS burst는 용량 계획 가정이다. k6 summary, Prometheus snapshot, RabbitMQ Management API snapshot에서 얻은 값만 `measured`로 보고한다. 측정 전에는 결과 칸에 `not measured`를 적고 가정을 결과처럼 서술하지 않는다.
