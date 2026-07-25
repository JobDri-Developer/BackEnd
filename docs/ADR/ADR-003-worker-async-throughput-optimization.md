# [ADR-003] Worker 비동기화 및 처리량 확장 우선 결정

## 상태 (Status)
- 작성일: 2026-07-24
- 상태: Proposed

## 맥락 (Context)
- `ADR-001`에서 `RabbitMQ + external worker + callback + SSE` 비동기 파이프라인을 채택했고, `ADR-002`에서는 "전면 reactive 전환"이 아니라 "병목이 확인된 외부 I/O 경로를 선택적으로 개선한다"는 방향을 정리했다.
- 이후 운영 환경에 Prometheus/Grafana 기반 계측을 추가해 Spring API와 external worker 양쪽의 병목을 실제 수치로 확인했다.
- 이번 결정은 다음 피드백을 실측 결과와 함께 재평가한 것이다.
  - "Consumer만 만들 거면 FastStream 고려"
  - "I/O 작업이 많아 보이니 이벤트 루프와 AsyncClient 전환 검토"
  - "현재가 단일 동기 스레드에 가까운 구조라면 worker 내부 동시성 모델을 바꿔야 한다"

- 모니터링 기준으로 확인된 핵심 수치는 다음과 같다.
  - `worker_task_queue_wait_duration_seconds` p95: 약 `30s`
  - `worker_task_processing_duration_seconds` p95: 약 `19.3s`
  - `llm_request_duration_seconds` p95: 약 `9.5s`
  - `worker_context_fetch_duration_seconds` p95: 약 `0.095s`
  - `worker_callback_duration_seconds` p95: 약 `0.234s`
  - `worker_task_inflight`: 대부분 `0~1`, 순간적으로만 `1`

- 이 수치는 다음 해석을 가능하게 한다.
  - 가장 큰 지연은 Spring API나 callback이 아니라 worker queue 대기 시간이다.
  - worker가 실제 처리에 들어간 뒤에도 전체 처리 시간의 큰 비중을 OpenAI 호출이 차지한다.
  - context fetch, callback latency는 모두 1초 미만으로 작아 1차 병목이 아니다.
  - inflight가 사실상 1에 가까운 것은 worker가 동시에 여러 작업을 충분히 소화하지 못하고 있음을 시사한다.

- 즉, 현재 구조의 핵심 문제는 "시스템 전체가 비동기가 아니다"가 아니라 아래 조합에 있다.
  - 외부 worker 내부의 blocking consume 흐름
  - worker 내부 HTTP/OpenAI I/O의 blocking execution
  - 낮은 프로세스당 동시 처리량
  - 그 결과 발생하는 queue 적체

- 반면 Spring API 서버 쪽 계측 결과는 다음 사실을 보여준다.
  - async task 상태 전이, queue wait, processing 기록은 가능하다.
  - 그러나 실제 OpenAI latency의 주된 실행 구간은 external worker 내부에 있으므로, Spring 전체를 WebFlux로 바꾸는 것이 현재 1차 해결책은 아니다.

## 결정 (Decision)
- 다음 리팩토링의 중심은 `Spring 전체 reactive 전환`이 아니라 `external worker의 처리량 확장과 내부 I/O async화`로 한다.
- 즉시 바꾸지 않는 것과 우선 바꾸는 것을 다음처럼 구분한다.
- 메인 API 서버의 현재 worker-facing contract truth는 [worker-internal-api-contract.md](/Users/shinae/Desktop/ceos/docs/worker-internal-api-contract.md)에 별도로 정리한다.

- 유지할 구조
  - `RabbitMQ + external worker + internal callback + SSE` 비동기 파이프라인
  - Spring API의 task 생성, 메시지 발행, 상태 저장, SSE 발행 구조
  - DB 중심 async task state 관리
  - callback / retry / DLQ / idempotency contract

- 우선 개편할 구조
  - worker의 MQ consume 모델
  - worker의 Spring internal API 호출 모델
  - worker의 OpenAI 호출 모델
  - worker의 task 유형별 동시성 제어 방식

- 이번 ADR의 결론은 다음 한 문장으로 요약한다.
  - 현재 필요한 리팩토링은 "시스템 전체 이벤트 루프 전환"이 아니라, "병목이 확인된 external worker를 async consumer + async HTTP + async OpenAI 구조로 전환해 프로세스당 처리량을 높이는 것"이다.

## 개선 구조 (Target Architecture)
- 목표 worker 구조는 다음과 같다.
  - broker consume: blocking consumer -> async consumer
  - internal API call: sync `requests` -> `httpx.AsyncClient`
  - OpenAI call: sync SDK/client -> async client
  - processing loop: message 1건 완전 종료 후 다음 message 처리 -> event loop 위에서 여러 I/O 대기 겹치기
  - concurrency: 무제한 병렬이 아니라 task type별 semaphore 또는 bounded concurrency 적용

- 이상적인 worker 처리 흐름은 아래와 같다.
  1. consumer가 RabbitMQ에서 메시지를 수신한다.
  2. task type별 동시성 제한 안에서 작업 슬롯을 획득한다.
  3. context fetch, candidate fetch, result store, callback 같은 internal API를 async HTTP client로 수행한다.
  4. OpenAI 호출을 async client로 수행한다.
  5. 성공/실패/재시도 여부를 callback contract에 맞춰 반영한다.
  6. 종료 시 inflight를 해제하고, retry/DLQ/idempotent delivery를 유지한다.

- 프레임워크 선택 기준은 다음과 같다.
  - FastAPI 자체를 유지하면서 async RabbitMQ client를 직접 붙이는 경로
  - 또는 consumer 중심 구조를 더 명확히 만들기 위해 FastStream을 도입하는 경로
  - 단, 어떤 프레임워크를 선택하든 핵심 성공 조건은 `MQ + HTTP + OpenAI I/O` 세 경로를 모두 async화하는 것이다.
  - 따라서 FastStream 도입은 "가능한 구현 수단"이지 "문제를 자동으로 해결하는 목적"은 아니다.

## 단계별 계획 (Implementation Plan)
- 0단계. 운영 튜닝 선반영
  - worker replica 수를 먼저 늘린다.
  - RabbitMQ prefetch 수와 현재 concurrency 상한을 점검한다.
  - `analysis`와 `jobposting`이 같은 처리 자원을 과도하게 경쟁하지 않는지 확인한다.
  - 이 단계의 목적은 코드 전환 전 저비용으로 queue wait를 줄일 수 있는지 검증하는 것이다.

- 1단계. worker observability 안정화
  - 이미 추가한 worker 메트릭을 기준 대시보드에 반영한다.
  - 필수 지표
    - `worker_task_queue_wait_duration_seconds`
    - `worker_task_processing_duration_seconds`
    - `llm_request_duration_seconds`
    - `worker_internal_api_duration_seconds`
    - `worker_task_retry_count_total`
    - `worker_task_inflight`
  - `task_type`, `operation`, `endpoint`, `outcome` 기준으로 라벨을 정리한다.
  - histogram bucket 상한이 현재 관측치보다 충분히 높도록 조정한다. 현재 queue wait p95가 이미 `30s` 수준이므로 상한 확장이 필요하다.

- 2단계. HTTP I/O async 전환
  - worker의 Spring internal API 호출을 `httpx.AsyncClient` 기반으로 변경한다.
  - 대상
    - context fetch
    - classification candidate fetch
    - result store
    - complete / failed / retry / finalize callback
  - retry/backoff/idempotent conflict 처리 규칙은 유지한다.

- 3단계. OpenAI 호출 async 전환
  - worker 내 job posting extract/classify/generate, analysis final 호출을 async client로 전환한다.
  - validation error와 retryable error를 명확히 구분해 재시도 폭주를 막는다.
  - LLM latency는 계속 operation label 기준으로 측정한다.

- 4단계. consume loop async 전환
  - blocking consumer를 event loop 기반 consumer로 전환한다.
  - 후보
    - async RabbitMQ client 직접 도입
    - FastStream 기반 consumer 재구성
  - 선택 기준
    - 현재 recovery spool / retry / ack/nack / DLQ 처리 모델을 쉽게 보존할 수 있는지
    - 운영 난이도 대비 도입 범위가 과도하지 않은지

- 5단계. task type별 처리량 분리
  - `analysis`와 `jobposting`이 서로 다른 I/O 패턴과 SLO를 가진다면 아래 중 하나를 적용한다.
    - task type별 concurrency limit 분리
    - task type별 queue consumer 분리
    - 필요 시 task type별 worker deployment 분리
  - 목적은 한쪽 적체가 다른 쪽 지연을 전염시키지 않게 하는 것이다.

- 6단계. 재측정과 승급 기준
  - 개선 전후 아래 지표를 비교한다.
    - queue wait p95 / p99
    - processing p95 / p99
    - LLM request p95
    - inflight 평균/최대
    - retry rate
  - 이번 측정 기준으로 아래 방향의 개선을 목표로 한다.
    - `queue wait p95 30s`를 우선적으로 큰 폭 감소
    - `worker processing p95 19.3s` 중 LLM 외 대기 구간 축소
    - inflight가 `1`에 고정되지 않도록 프로세스당 유효 동시 처리량 확보

## 대안 비교 (Alternatives Considered)
- Spring 전체 WebFlux / event loop 전환
  - 현재 측정 기준으로는 우선순위가 낮다.
  - context fetch와 callback은 이미 p95가 `0.095s`, `0.234s` 수준으로 작다.
  - 1차 병목은 Spring 웹 레이어가 아니라 external worker queue wait와 worker 내부 LLM latency다.

- 현 구조 유지 + worker replica만 확장
  - 단기 완화책으로는 유효하다.
  - 다만 processing p95가 `19.3s`, LLM p95가 `9.5s`인 만큼, 장기적으로는 worker 1프로세스당 처리량 한계를 그대로 남긴다.

- FastStream 즉시 도입
  - consumer 중심 구조 정리에는 장점이 있다.
  - 그러나 async HTTP/OpenAI 전환 없이 프레임워크만 교체하면 병목의 본질은 남는다.
  - 따라서 "도입 가능" 대안으로 유지하되, 도입 자체를 목표로 삼지 않는다.

## 결과 (Consequences)
- 장점(Pros)
  - 실제 수치에 근거한 개편이라 논의가 추상적 취향 싸움으로 흐르지 않는다.
  - 가장 큰 병목인 queue wait와 worker 내부 I/O를 직접 겨냥한다.
  - Spring 전체를 불필요하게 reactive로 재작성하는 비용을 피할 수 있다.
  - 단계별 전환이 가능해 장애 범위를 줄일 수 있다.
  - worker 관측 가능성이 높아져 이후 replica, prefetch, concurrency 한도 조정도 더 정밀해진다.

- 단점 및 리스크(Cons)
  - worker async 전환은 ack/nack, shutdown, recovery spool, retry 동작까지 함께 검증해야 해 구현 난이도가 있다.
  - async consumer와 기존 blocking consumer가 전환 기간 동안 공존하면 운영 복잡도가 증가할 수 있다.
  - concurrency 상한 설계를 잘못하면 OpenAI rate limit 또는 Spring internal API 부하가 새 병목이 될 수 있다.
  - 프레임워크 전환(FastStream 포함)은 측정 지표와 계약 호환성을 유지하면서 진행해야 하므로 단계적 검증이 필요하다.
