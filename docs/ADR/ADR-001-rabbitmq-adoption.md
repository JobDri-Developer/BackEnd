# [ADR-001] RabbitMQ 도입 결정

## 상태 (Status)
- 작성일: 2026-07-10
- 상태: Accepted

## 맥락 (Context)
- `JobDri`는 채용 공고 ingest, 자소서 분석처럼 수 초에서 수십 초 이상 걸릴 수 있는 AI 작업을 처리한다.
- 이 작업들은 HTTP 요청-응답 한 번으로 끝나지 않고, `Spring API -> 메시지 발행 -> 외부 FastAPI worker 처리 -> internal callback -> DB 저장 -> SSE 상태 전파` 흐름을 가진다.
- 따라서 웹 요청 스레드와 무거운 작업 실행을 분리하고, 서버 재시작이나 일시 장애가 있어도 작업 유실 가능성을 낮춰야 했다.
- 작업 종류도 하나가 아니라 `job posting ingest`, `analysis`처럼 성격이 다른 비동기 작업이 공존하므로, 라우팅과 운영 분리가 가능한 구조가 필요했다.
- 이미 Redis는 로그아웃, 토큰 무효화, 실시간 상태 보조 용도로 사용하고 있지만, 본 프로젝트의 핵심 요구는 "캐시"보다 "안전한 작업 전달과 소비"에 더 가깝다.
- 비교 대상은 다음과 같았다.
  - `@Async`: Spring 내부 스레드풀 기반 비동기 처리
  - Redis Queue/List/Stream: Redis를 큐처럼 사용하는 방식
  - 기타: DB Polling Queue, Kafka, AWS SQS 같은 대안

## 결정 (Decision)
- 비동기 작업 전달 계층으로 RabbitMQ를 채택한다.
- Spring 서버는 RabbitMQ의 durable exchange/queue에 작업 메시지를 발행하고, 외부 FastAPI worker는 이를 consume 하도록 구성한다.
- 작업 유형별로 queue와 routing key를 분리하고, 메시지는 persistent delivery mode로 발행한다.
- 발행 시 publisher confirm과 returned message를 확인해 "브로커에 실리지 않은 작업"을 조기에 감지한다.
- 실패 작업을 분리 추적할 수 있도록 Dead Letter Queue(DLQ)를 둔다.
- 실제 사용자 상태는 DB의 async task row를 기준으로 관리하고, worker callback 이후 SSE로 상태를 전파한다.

- RabbitMQ를 선택한 핵심 이유는 다음과 같다.
  - `@Async`는 같은 Spring 프로세스 안에서만 동작하므로 외부 worker와의 자연스러운 분리가 어렵다.
  - `@Async`는 애플리케이션 재시작, 인스턴스 분리, 소비자 독립 스케일링, 메시지 축적 같은 요구에 약하다.
  - Redis Queue는 구현은 단순하지만, 본 프로젝트에서 필요한 "명시적 큐 운영", "실패 메시지 분리", "메시지 브로커로서의 역할" 측면에서 RabbitMQ보다 설계 부담이 커질 수 있다.
  - Kafka는 대용량 이벤트 스트리밍에는 강하지만, 현재 요구사항은 이벤트 분석 플랫폼보다 "작업 큐 + 워커 처리 + 재시도/실패 관리"에 가깝기 때문에 운영 복잡도 대비 이점이 과했다.
  - DB Polling Queue는 별도 브로커 없이 시작할 수 있지만, polling 비용, 락 경쟁, 지연 증가, 운영 확장성 측면에서 장기적으로 불리하다.
  - RabbitMQ는 현재 요구 수준에서 라우팅, 안정적 전달, 소비자 분리, 재처리 운영성을 균형 있게 제공한다.

## 결과 (Consequences)
- 장점(Pros)
  - Spring API 서버와 외부 worker를 느슨하게 분리할 수 있어 배포, 장애 격리, 스케일링이 쉬워진다.
  - 메시지를 durable queue와 persistent message로 다뤄 작업 유실 가능성을 줄일 수 있다.
  - publisher confirm, DLQ, routing key 기반 분리 덕분에 운영 중 실패 원인 추적이 쉬워진다.
  - 긴 AI 작업이 웹 요청 스레드를 점유하지 않아 API 응답성과 사용자 경험을 지키기 좋다.
  - 작업량이 증가하면 worker consumer 수를 늘려 처리량을 유연하게 확장할 수 있다.
  - `job posting ingest`와 `analysis`를 서로 다른 큐 정책으로 운영할 수 있어 도메인별 튜닝이 가능하다.

- 단점 및 리스크(Cons)
  - RabbitMQ 인프라와 운영 지식이 추가로 필요하므로 개발/운영 복잡도가 증가한다.
  - 메시지 시스템은 기본적으로 at-least-once 처리 성격이 있으므로, 중복 소비를 견딜 수 있는 idempotent 설계가 필요하다.
  - 큐 적체, DLQ 누적, consumer 장애를 지속적으로 모니터링하지 않으면 장애 발견이 늦어질 수 있다.
  - 단순 메일 발송처럼 프로세스 내부에서 짧게 끝나는 작업에는 RabbitMQ가 과할 수 있으므로, 모든 비동기 작업에 일괄 적용하면 안 된다.
  - 향후 초고속 이벤트 스트리밍, 대규모 이벤트 리플레이가 핵심 요구가 되면 Kafka나 별도 이벤트 플랫폼 검토가 다시 필요할 수 있다.
