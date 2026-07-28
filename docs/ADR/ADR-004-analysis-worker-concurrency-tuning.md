# [ADR-004] Analysis Worker 동시성 상향 실험 및 남은 병목 재정의

## 상태 (Status)
- 작성일: 2026-07-28
- 상태: Proposed

## 맥락 (Context)
- `ADR-002`에서는 현재 구조를 "비동기 작업 접수 플로우는 이미 갖추고 있으나, 실제 병목은 worker 내부 실행 구간에 있다"는 관점으로 정리했다.
- `ADR-003`에서는 external worker를 async consumer + async HTTP + async OpenAI 방향으로 전환하고, queue wait / processing / LLM latency를 기준으로 처리량을 확장하기로 결정했다.
- 이후 analysis worker는 실제로 다음 async 경로를 사용하도록 정리되었다.
  - RabbitMQ consume: async runtime 기반 consume
  - Spring internal API 호출: `httpx.AsyncClient`
  - OpenAI analysis 호출: async client
- 그러나 async 전환 이후에도 `analysis` task의 유효 동시 처리량은 기본적으로 `1`에 가까웠다.
  - `WORKER_ANALYSIS_CONCURRENCY_LIMIT` 기본값이 `1`이었다.
  - prefetch 역시 낮게 설정되어 있으면 broker에서 메시지를 더 가져오지 못했다.
- 즉, "worker가 async 구조인지"와 "실제로 동시에 몇 개의 task를 처리하는지"는 별개의 문제였다.
- 이번 실험의 목적은 `analysis` task에 대해 concurrency/prefetch를 `1 -> 2 -> 3`으로 점진 상향했을 때 실제 병목이 어떻게 이동하는지 확인하는 것이었다.

## 실험 기준 (Experiment Setup)
- 대상 task type: `analysis`
- 비교 조건
  - 1차 기준: `concurrency=1`
  - 2차 기준: `concurrency=2`, `prefetch=2`
  - 3차 기준: `concurrency=3`, `prefetch=3`
- 부하 방식
  - 동일한 analysis 요청을 짧은 시간에 여러 건 연속 제출
  - 대표 비교는 12건 연속 요청 기준으로 관찰
- 관측 지표
  - `worker_task_queue_wait_duration_seconds`
  - `worker_task_processing_duration_seconds`
  - `llm_request_duration_seconds{task_type="analysis", operation="analysis-final"}`
  - `worker_task_inflight`
  - `http_server_requests_seconds` 기반 analysis submit API latency
  - retry/failure/terminal outcome

## 관측 결과 (Observed Results)
### 1. `concurrency=1` 구간
- analysis worker는 사실상 한번에 1개 task만 처리했다.
- queue wait가 가장 먼저 크게 누적되었다.
- 이전 측정 및 `ADR-003` 기준 대표 수치
  - `worker_task_queue_wait_duration_seconds` p95: 약 `30s`
  - `worker_task_processing_duration_seconds` p95: 약 `19.3s`
  - `llm_request_duration_seconds` p95: 약 `9.5s`
  - `worker_task_inflight`: 대부분 `0~1`
- 해석
  - 전체 플로우는 비동기였지만, worker의 유효 동시 처리량이 `1`이라 사용자 요청은 앞선 요청들의 worker 점유 시간만큼 순차 대기했다.
  - 이 단계의 핵심 병목은 `queue wait`였다.

### 2. `concurrency=2`, `prefetch=2` 구간
- inflight가 `2`까지 올라가며 analysis task 2건 동시 처리가 가능해졌다.
- queue wait는 `1` 대비 눈에 띄게 감소했다.
- 대표 관측
  - `worker_task_queue_wait_duration_seconds` p95/p99: 약 `9~10s`
  - `worker_task_inflight max`: `2`
  - `analysis submit p95`: 대략 `2.5s` 전후
- 반면 다음 지표는 크게 줄지 않았다.
  - `worker_task_processing_duration_seconds` p95: 여전히 `20s`대 후반 구간 관측
  - `llm_request_duration_seconds{operation="analysis-final"}` p95: 여전히 `20s`대 후반 구간 관측
- 해석
  - worker 직렬 병목은 일부 완화되었지만, task 하나가 worker 슬롯을 오래 점유하는 문제는 그대로 남아 있었다.
  - 병목의 무게 중심이 `queue wait only`에서 `queue wait + long LLM call`로 이동했다.

### 3. `concurrency=3`, `prefetch=3` 구간
- inflight가 `3`을 활용하는 방향으로 확장되었다.
- `2 -> 3` 증설 효과는 `1 -> 2`만큼 크지는 않았지만, queue wait와 submit latency는 추가로 개선되었다.
- 대표 관측
  - `analysis submit p95`: 약 `1.9~2.1s`
  - `worker_task_queue_wait_duration_seconds` p95: 약 `6.6~7.0s`
  - `worker_task_queue_wait_duration_seconds` p99: 약 `9.8s`
  - `worker_task_processing_duration_seconds` p95: 약 `26~28s`
  - `llm_request_duration_seconds{operation="analysis-final"}` p95: 약 `22~27s`
  - retry: `No data`
  - terminal outcome: 모두 `succeeded`
- 해석
  - 안정성 저하 없이 queue wait는 더 줄었다.
  - 그러나 processing/LLM latency는 큰 폭으로 줄지 않았다.
  - 즉, `concurrency=3`은 "더 많은 요청을 동시에 태울 수 있게 해 queue 적체를 완화"했지만, "개별 analysis task의 LLM 수행 시간"은 거의 그대로 남아 있다.

## 해석 (Interpretation)
- 이번 실험은 다음 사실을 확인해준다.

### 1. worker는 현재 async 구조가 맞다
- analysis worker는 Rabbit consume, internal API, OpenAI 호출을 async 경로로 수행한다.
- 따라서 "worker가 아직 동기 구조라서 느리다"는 설명은 현재 시점에서는 정확하지 않다.

### 2. 하지만 async 구조만으로 처리량이 자동 확장되지는 않는다
- async worker라도 concurrency limit가 `1`이면 유효 동시 처리량은 여전히 `1`이다.
- 즉, 이전에는 "blocking worker + inflight 1"에 가까운 상태였다면, 지금은 "async worker지만 inflight 1로 제한되면 체감상 비슷하게 느릴 수 있는 상태"였다.

### 3. `1 -> 2 -> 3` 상향은 queue 적체를 줄이는 데 유효했다
- 동일 시간에 여러 요청이 들어오면, 더 많은 worker slot이 동시에 task를 붙잡을 수 있다.
- 그 결과 앞 요청들의 완료를 순차적으로 오래 기다리던 구조가 완화되었다.
- 예를 들어 9개의 요청이 짧은 시간에 들어온다면
  - `concurrency=1`에서는 사실상 9건이 거의 직렬에 가깝게 처리된다.
  - `concurrency=3`에서는 3건씩 병렬로 묶여 처리되므로, 뒤쪽 사용자가 기다리는 선행 queue 시간이 줄어든다.

### 4. 그러나 남은 주 병목은 `analysis-final` LLM 호출 시간이다
- `concurrency=3`에서도 `worker_task_processing_duration_seconds` p95와 `llm_request_duration_seconds{operation="analysis-final"}` p95가 여전히 `20s`대다.
- context fetch, result store, callback보다 analysis-final 응답 생성 시간이 훨씬 길다.
- 따라서 현재 단계에서는 "큐 자체"보다 "worker slot 하나를 오래 점유하는 analysis-final LLM 호출"이 더 본질적인 tail latency 원인이다.

## 결정 (Decision)
- 현재 운영 후보값은 다음을 기본으로 본다.
  - `WORKER_ANALYSIS_CONCURRENCY_LIMIT=3`
  - `WORKER_PREFETCH_COUNT=3`
- 이유는 다음과 같다.
  - `1 -> 2`에서 queue wait 개선 효과가 컸다.
  - `2 -> 3`에서도 개선 폭은 작지만 추가 감소가 확인되었다.
  - retry/failure 증가 없이 성공적으로 처리되었다.
- 다만 이번 ADR의 결론은 "concurrency만 계속 올리면 된다"가 아니다.
- 이번 결정의 핵심은 다음과 같다.
  - `analysis` worker의 병목은 이제 "동기 worker 부재"가 아니라 "긴 analysis-final LLM 수행 시간"으로 재정의한다.
  - 즉, concurrency 상향은 queue 적체 완화용으로는 유효하지만, 개별 task latency 단축의 주된 해법은 아니다.

## 개선된 점 (What Improved)
- `analysis` task의 유효 동시 처리량이 `1`에서 `3`까지 확대되었다.
- queue wait p95가 `30s -> 9~10s -> 6~7s` 수준으로 내려왔다.
- 다건 요청이 짧은 시간에 유입될 때 뒤쪽 요청이 앞선 요청의 전체 처리시간을 그대로 직렬 대기하던 문제가 완화되었다.
- submit API p95 역시 `2s` 안팎으로 개선되었다.
- retry/failure 증가 없이 안정적으로 처리되었다.

## 개선하지 못한 점 (What Did Not Improve Enough)
- `analysis-final` LLM p95는 여전히 `20s`대다.
- `worker_task_processing_duration_seconds` p95 역시 여전히 `20s`대 후반이다.
- 즉, worker slot 수는 늘었지만 각 slot이 한번 잡은 작업을 놓는 데 걸리는 시간은 크게 줄지 않았다.
- tail latency 관점에서는 `queue wait p99`가 아직 `10s` 내외로 남아 있다.

## 다음 단계 (Next Step)
- 다음 최적화 우선순위는 concurrency 추가 상향보다 `analysis-final` 호출 최적화다.
- 우선 검토 항목
  - analysis prompt 길이 축소
  - 문항별 분석 개수 제한 강화
  - 출력 스키마 단순화
  - substring 강제 규칙 완화
  - `max_output_tokens` 도입 검토
  - 필요 시 `analysis-final`을 다단계 분석으로 분리
- 이후에도 queue wait가 다시 높아진다면 그때 `3 -> 4` 이상 상향을 재측정한다.
- 단, 그 경우에도 아래 조건을 함께 확인해야 한다.
  - OpenAI rate limit 증가 여부
  - internal API 오류율 증가 여부
  - retry 증가 여부

## 결과 (Consequences)
- 장점(Pros)
  - async worker 전환 이후 실제 처리량을 수치로 검증할 수 있다.
  - concurrency tuning이 queue 적체 완화에 실제 효과가 있음을 확인했다.
  - 현재 남은 병목을 "queue"에서 "analysis-final LLM time"으로 더 정확히 좁힐 수 있다.
  - 이후 최적화 범위를 프롬프트/출력 구조/LLM 설정으로 집중할 수 있다.

- 단점 및 리스크(Cons)
  - concurrency만 높이는 방식은 개별 task latency를 근본적으로 줄이지 못한다.
  - slot을 늘릴수록 OpenAI rate limit, 내부 API 부하, 비용 증가 가능성이 있다.
  - `2 -> 3` 증설 효과가 제한적이었던 만큼, 추가 증설은 체감 대비 비용이 커질 수 있다.
  - 현재 구조에서는 `analysis-final` 호출이 길면 여전히 worker slot을 오래 점유해 tail latency가 남는다.
