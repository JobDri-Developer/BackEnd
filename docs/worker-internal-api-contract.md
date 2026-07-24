# Worker Internal API Contract

## 목적

이 문서는 external worker가 Spring API 서버와 통신할 때 따라야 하는 현재 계약을 구현 기준으로 정리한다.

- 범위
  - `analysis` worker internal API
  - `jobposting` worker internal API
  - idempotency, retry, terminal state 재호출 동작
  - worker async 전환 이후에도 유지해야 하는 서버 기대값
- 비범위
  - Spring WebFlux 전환
  - worker 프레임워크 선택
  - OpenAI async client 구현 방식

관련 ADR: [ADR-003](/Users/shinae/Desktop/ceos/docs/ADR/ADR-003-worker-async-throughput-optimization.md)

## 공통 계약

- 모든 internal API는 `X-Internal-Api-Key` 헤더를 요구한다.
- 응답은 `ApiResponse` envelope로 반환된다.
- 현재 서버는 worker callback에서 `409 Conflict`를 멱등 성공 신호로 사용하지 않는다.
- 중복/무결성 충돌은 전역 예외 처리에서 보통 `400 Bad Request`로 매핑된다.
- terminal 상태 재호출의 멱등성은 HTTP status보다 서비스 레이어 no-op 또는 기존 결과 재반환으로 보장된다.
- worker는 네트워크 타임아웃 또는 응답 유실이 발생한 경우 `GET /tasks/{taskId}` 또는 `GET /tasks/{taskId}/result`로 상태를 확인한 뒤 후속 재시도를 결정하는 것을 기본 전략으로 삼는다.

## 공통 상태 코드 해석

| 상태 코드 | 현재 의미 | worker 해석 |
| --- | --- | --- |
| `200 OK` | 정상 처리, 또는 terminal 상태 재호출에 대한 no-op/기존 결과 반환 | 성공으로 간주 |
| `400 Bad Request` | 잘못된 payload, 이미 실패한 terminal task, 중복/무결성 충돌 | 자동 무한 재시도 금지, 상태 조회 후 판단 |
| `403 Forbidden` | task identity 불일치 등 요청 주체/대상 검증 실패 | 재시도보다 payload 교정 필요 |

## 서버 측 task timeout 기준

이 값은 worker HTTP 호출 timeout이 아니라 Spring API가 async task를 만료 처리하는 기준이다.

| Task Type | Pending queue timeout | Running processing timeout | 근거 |
| --- | --- | --- | --- |
| `analysis` | 기본 `10분` | 기본 `20분` | `AnalysisAsyncSweepService` 기본값 |
| `jobposting` | 기본 `10분` | 기본 `20분` | `app.worker.job-posting.*-timeout-minutes` |

## Analysis Contract

### 권장 호출 순서

`context -> running -> result -> complete`

오류 경로는 `retry` 또는 `failed`를 사용한다.

### Endpoint 계약표

| Endpoint | 목적 | 요청 식별자 | 재시도/멱등 동작 | worker 메모 |
| --- | --- | --- | --- | --- |
| `POST /api/internal/worker/analysis/context` | 분석 실행 컨텍스트 조회 | `taskId + userId + mockApplyId` | 같은 taskId 재호출 가능 | 첫 성공 시 크레딧 1회 예약을 동반한다. 재호출이어도 크레딧은 중복 차감되지 않아야 한다. |
| `POST /api/internal/worker/analysis/tasks/{taskId}/running` | task를 `RUNNING`으로 전이 | path `taskId` | 이미 `SUCCEEDED`/`FAILED`면 `200` no-op | 네트워크 불확실 시 재호출 가능 |
| `POST /api/internal/worker/analysis/tasks/{taskId}/result` | complete 전 durable result 저장 | `taskId + userId + mockApplyId` | 같은 taskId 기준 upsert | 같은 payload 재전송은 성공으로 처리한다. terminal task여도 저장 자체는 no-op 성격으로 흡수한다. |
| `POST /api/internal/worker/analysis/tasks/{taskId}/complete` | 결과 반영 후 성공 종료 | `taskId + userId + mockApplyId` | 이미 `SUCCEEDED`면 기존 분석 결과를 반환, 이미 `FAILED`면 `400` | 네트워크 유실 시 가장 먼저 재조회/재호출 후보 |
| `POST /api/internal/worker/analysis/tasks/{taskId}/retry` | 재시도 예정 상태 반영 | path `taskId` | 이미 `SUCCEEDED`/`FAILED`면 `200` no-op | `retryCount`, `failureReason`, `queueLatencyMillis` 반영 |
| `POST /api/internal/worker/analysis/tasks/{taskId}/failed` | 최종 실패 반영 | path `taskId` | 이미 `SUCCEEDED`/`FAILED`면 `200` no-op | reserved credit가 있으면 환불 처리 |
| `GET /api/internal/worker/analysis/tasks/{taskId}` | 현재 task 상태 조회 | path `taskId` | 조회성 | callback 결과 확인용 |
| `GET /api/internal/worker/analysis/tasks/{taskId}/result` | 저장된 result payload 조회 | path `taskId` | 조회성 | `complete` 전후 payload 복구용 |

### Analysis 추가 규칙

- `context`와 `complete`는 `userId`, `mockApplyId`가 task row와 일치해야 한다.
- `complete`는 내부적으로 먼저 result를 upsert한 뒤 최종 성공 처리한다.
- `result` 선저장은 선택 사항이지만, worker async 전환 이후에는 네트워크 불확실성 대응을 위해 권장 경로로 본다.
- `retry`와 `failed`는 `queueLatencyMillis`를 통해 queue wait 관측값을 상태 row에 남길 수 있다.

## JobPosting Contract

### 권장 호출 순서

주 경로:

`ingest/context -> tasks/{taskId}/running -> classification/candidates -> tasks/{taskId}/result -> ingest/finalize`

호환 경로:

`tasks/{taskId}/complete`

`complete`는 worker가 이미 최종 `JobPostingIngestResponse`를 조립한 경우에만 쓰는 legacy compatibility 경로다. 현재 저장/완료 일체형 메인 경로는 `ingest/finalize`다.

### Endpoint 계약표

| Endpoint | 목적 | 요청 식별자 | 재시도/멱등 동작 | worker 메모 |
| --- | --- | --- | --- | --- |
| `POST /api/internal/worker/job-postings/ingest/context` | 이미지 접근용 readable URL 발급 | 현재는 `userId + imageObjectKey` | 재호출 가능 | 현재 구현은 `taskId`를 받지 않는다. task 상태와 직접 결합된 검증은 없다. |
| `POST /api/internal/worker/job-postings/classification/candidates` | 추출 결과 기반 분류 후보 조회 | 추출 payload | 재호출 가능 | 현재 구현은 stateless 조회성 endpoint다. |
| `POST /api/internal/worker/job-postings/tasks/{taskId}/running` | task를 `RUNNING`으로 전이 | path `taskId` | 이미 `SUCCEEDED`/`FAILED`면 `200` no-op | analysis와 동일 패턴 |
| `POST /api/internal/worker/job-postings/tasks/{taskId}/result` | finalize 전 durable finalize payload 저장 | `taskId + userId + result.taskId` | 같은 taskId 기준 upsert | `result.taskId`가 path와 일치해야 한다. 같은 payload 재전송은 성공으로 처리한다. |
| `POST /api/internal/worker/job-postings/ingest/finalize` | 공고 저장 후 성공 종료 | `taskId + userId` | 이미 `SUCCEEDED`면 기존 결과 반환, 이미 `FAILED`면 `400` | 현재 주 성공 callback |
| `POST /api/internal/worker/job-postings/tasks/{taskId}/complete` | 완성된 `JobPostingIngestResponse`로 즉시 성공 종료 | path `taskId` | `markSuccess` 기준으로 성공 재호출 시 기존 결과 반환, 실패 task면 `400` | legacy compatibility completion 경로 |
| `POST /api/internal/worker/job-postings/tasks/{taskId}/retry` | 재시도 예정 상태 반영 | path `taskId` | 이미 `SUCCEEDED`/`FAILED`면 `200` no-op | `retryCount`, `failureReason`, `queueLatencyMillis` 반영 |
| `POST /api/internal/worker/job-postings/tasks/{taskId}/failed` | 최종 실패 반영 | path `taskId` | 이미 `SUCCEEDED`/`FAILED`면 `200` no-op | 실패 알림과 상태 전이 수행 |
| `GET /api/internal/worker/job-postings/tasks/{taskId}` | 현재 task 상태 조회 | path `taskId` | 조회성 | callback 결과 확인용 |
| `GET /api/internal/worker/job-postings/tasks/{taskId}/result` | 저장된 finalize payload 조회 | path `taskId` | 조회성 | finalize 재시도 전 payload 복구용 |

### JobPosting 추가 규칙

- `context`, `classification/candidates`는 현재 task-bound contract가 아니라 worker helper API 성격이 강하다.
- `result`는 `JOB_POSTING_FINALIZE` payload 저장용이고, `complete`는 `JOB_POSTING_COMPLETE` 저장용이라 내부 결과 타입이 다르다.
- `ingest/finalize`는 저장까지 수행하는 주 경로이므로, worker가 network timeout을 겪으면 즉시 중복 생성 재시도하기보다 `GET /tasks/{taskId}`로 최종 상태를 먼저 확인해야 한다.

## Retry 판단 가이드

| 상황 | 권장 동작 |
| --- | --- |
| `running`, `retry`, `failed` 호출 응답 유실 | 같은 요청 재호출 가능 |
| `result` 저장 응답 유실 | `GET /tasks/{taskId}/result` 확인 후 필요 시 재저장 |
| `analysis complete` 응답 유실 | `GET /tasks/{taskId}` 확인 후 필요 시 `complete` 재호출 |
| `jobposting finalize` 응답 유실 | `GET /tasks/{taskId}` 우선 확인, 성공 미확정일 때만 재호출 |
| `400` 수신 | payload/terminal state/duplicate 가능성 확인, 무조건 재시도하지 않음 |
| `403` 수신 | identity mismatch 가능성이 높으므로 재시도보다 요청 값 검증 우선 |

## 이번 이슈 범위에서 명시하는 비범위

- 이번 작업은 Spring MVC를 WebFlux/reactive stack으로 전환하지 않는다.
- 현재 1차 병목은 Spring callback latency보다 worker queue wait와 worker 내부 LLM I/O다.
- 따라서 API 서버는 worker async 전환을 안전하게 받기 위한 contract truth와 운영 기준 정리에 집중한다.
