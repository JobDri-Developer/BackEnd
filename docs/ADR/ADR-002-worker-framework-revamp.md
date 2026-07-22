# [ADR-002] AI Worker 프레임워크 개편 방향 결정

## 상태 (Status)
- 작성일: 2026-07-22
- 상태: Proposed

## 맥락 (Context)
- `JobDri`의 긴 AI 작업 흐름은 이미 `Spring API -> RabbitMQ -> 외부 worker -> internal callback -> DB 저장 -> SSE 상태 전파` 형태로 분리되어 있다.
- 따라서 현재 구조를 "완전히 단일 동기 스레드 서버"라고 보기는 어렵다. Spring 서버에도 작업 유형별 비동기 executor가 있고, 사용자 관점에서도 요청-응답과 실제 작업 수행이 분리된 비동기 처리 흐름을 제공하고 있다.
- 다만 "서비스 흐름의 비동기"와 "워커 내부 실행의 논블로킹 async"는 다른 문제다.
- 현재 Spring의 일부 외부 I/O는 여전히 blocking 방식이다. OpenAI 호출은 `OpenAIOkHttpClient` 기반으로 구성되어 있고, 실제 생성/분석 로직도 `openAIClient.responses().create(...)`를 동기 호출로 사용한다.
- 동시에 LLM 호출량은 이벤트 루프가 아니라 `Semaphore` 기반 `LlmConcurrencyLimiter`로 제한하고 있다. 즉, 현재 병목 후보는 event loop 부재 자체라기보다 `blocking I/O + worker thread 점유 + 동시성 제한` 조합에 가깝다.
- 외부 Python worker 역시 현재 운영 구조상 "큐 뒤의 백그라운드 처리"이긴 하지만, 워커 내부는 메시지 하나를 받아 외부 HTTP/OpenAI I/O를 blocking으로 수행하는 흐름에 가깝다. FastAPI를 사용한다는 사실만으로 자동으로 논블로킹 worker가 되는 것은 아니다.
- 이런 상황에서 "이벤트 루프로 바꾸고 AsyncClient로 전환하자"는 피드백은 방향성 자체는 타당하지만, 곧바로 전면 개편으로 받아들이기에는 판단 근거가 부족하다.
- 먼저 실제 병목이 어디인지 구분해야 한다. 주요 후보는 다음과 같다.
  - RabbitMQ 적체 또는 consumer 처리량 부족으로 인한 `queue wait time`
  - OpenAI, retrieval, internal callback 같은 외부 I/O의 `latency`
  - `LlmConcurrencyLimiter` permit 획득 지연, executor queue 누적 같은 내부 `thread starvation`

## 결정 (Decision)
- 현재의 `RabbitMQ + external worker + callback + SSE` 비동기 파이프라인은 유지한다.
- 현 시점에서 Spring API 서버나 전체 worker를 "전면 event loop 아키텍처"로 즉시 재작성하지 않는다.
- 이번 이슈는 "시스템이 비동기가 아니다"가 아니라, "비동기 작업 흐름 위에서 일부 worker/LLM 호출 구간이 blocking I/O 중심이라 처리량 한계가 생길 수 있다"는 문제로 정의한다.
- 따라서 1차 대응은 프레임워크 교체가 아니라 관측 가능성 확보와 병목 식별로 한다.

- 우선 측정할 핵심 지표는 다음과 같다.
  - `queue wait time = worker 시작 시각 - 작업 접수 시각`
  - `processing time = 완료 시각 - worker 시작 시각`
  - `end-to-end time = 완료 시각 - 작업 접수 시각`
  - `OpenAI latency = create 호출 직후 - 호출 직전`
  - `retrieval latency`, `internal callback latency`, 필요 시 `S3/DB 주변 I/O 시간`
  - `llm.concurrency.acquire.duration`, `llm.request.timeout.count`, `availablePermits`
  - executor active thread 수, executor queue size, rejection 여부, retry 횟수

- 운영 개선 우선순위는 다음 순서를 따른다.
  - worker 인스턴스 수평 확장과 consumer 수 조정
  - RabbitMQ prefetch, 재시도 정책, 현재 thread pool 및 semaphore 한도 튜닝
  - 병목이 특정 외부 호출에 집중될 경우 해당 I/O 경로의 최적화
  - 그 이후에도 프로세스당 유휴 대기 시간이 크고 처리량 개선이 제한적일 때 worker 내부 async 전환 검토

- async 전환이 필요하다고 확인되면, 외부 worker부터 점진적으로 전환한다.
  - 대상 예시: async RabbitMQ client, `httpx.AsyncClient` 같은 async HTTP client, event loop 기반 consume/ack 흐름
  - 단, task contract, callback API, SSE 상태 모델, retry/DLQ, idempotency는 유지한다.
  - task 유형별 또는 worker 경로별 단계적 전환을 원칙으로 하며, big-bang rewrite는 지양한다.

- Spring 서버는 당장 전체를 WebFlux/event loop 기반으로 바꾸지 않는다.
  - 먼저 실제 병목이 확인된 외부 API 호출 구간에 한해 async client 또는 별도 non-blocking adapter 적용 가능성을 검토한다.
  - 즉, "전면 전환"보다 "측정 기반의 선택적 전환"을 기본 전략으로 삼는다.

## 결과 (Consequences)
- 장점(Pros)
  - "사용자 경험상 비동기"와 "worker 내부 논블로킹 실행"을 구분해 논의를 정리할 수 있다.
  - 실제 병목 데이터 없이 전면 재작성하는 비용과 리스크를 줄일 수 있다.
  - 수평 확장, prefetch, semaphore/thread pool 튜닝 같은 저비용 개선부터 적용할 수 있다.
  - 이후 async 전환이 필요해져도 지표와 기준이 명확해져 의사결정이 쉬워진다.
  - callback, SSE, DLQ, retry, task 상태 저장 같은 현재 운영 안정성을 유지한 채 개편을 진행할 수 있다.

- 단점 및 리스크(Cons)
  - 초기에는 계측과 대시보드 구성이 추가로 필요하므로 즉각적인 체감 개선이 없을 수 있다.
  - 병목이 이미 심각한 경우 단계적 접근이 전면 개편보다 느리게 보일 수 있다.
  - blocking 경로는 측정과 전환이 끝날 때까지 계속 남아 있으므로, 고부하 상황에서 thread 점유 문제가 지속될 수 있다.
  - 전환 기간 동안에는 blocking worker와 async worker 전략이 공존할 수 있어 운영 복잡도가 일시적으로 증가한다.
