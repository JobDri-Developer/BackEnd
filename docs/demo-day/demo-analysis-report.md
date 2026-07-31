# JobDri 데모데이 분석 설계 보고서

## 1. 결론

현재 구조 그대로는 네 상태를 한 결과 화면에 안정적으로 노출할 수 없었다. 운영 Python 워커는 PROVEN을 생성했지만 Spring API가 저장 전에 제거했고, MISSING은 별도 배열로만 저장되는데 프론트가 렌더링하지 않았으며, 프론트의 MENTIONED/FABRICATED 한글 라벨이 서로 뒤바뀌어 있었다.

최소 호환 변경 후에는 다음 방식으로 네 상태를 함께 보여 줄 수 있다.

- PROVEN, MENTIONED, FABRICATED: 기존 `questionAnalyses`에 저장하고 원문 문장을 하이라이트한다.
- MISSING: 원문 문장이 없으므로 기존 `missingKeywords`에 저장하고 결과 개요에서 `요건 누락`으로 표시한다.
- 기존 DB 컬럼과 API 필드는 유지했다. 데모 전용 플래그, 특정 문장 하드코딩, 점수 강제 로직은 추가하지 않았다.

## 2. 실제 분석 파이프라인 조사 결과

| 구간 | 확인한 구현 | 조사 결과 |
|---|---|---|
| 분석 요청 | `AnalysisController` → `AnalysisAsyncFacadeService` | `POST /api/mock-applies/{mockApplyId}/analysis`가 비동기 분석 작업을 만든다. |
| 운영 AI 실행 | Python `AnalysisOpenAiWorker` | RabbitMQ 작업을 소비한 Python 워커가 실제 OpenAI Responses API를 호출한다. |
| 완료 연결 | `AnalysisWorkerBridgeService.completeTask` | 워커의 `llmResponse`를 `AnalysisService.finalizeAnalysis`에 전달한다. |
| 저장/조회 | `AnalysisService`, `Analysis`, `QuestionAnalysis` | 점수·강약점·누락 키워드와 문장 분석을 저장한 뒤 `AnalysisResponse`로 조회한다. |
| 문장 위치 | `AnalysisService.findNextSentenceStart` | 모델이 index를 만들지 않고, 서버가 답변의 정확한 substring을 찾아 Java 문자열 index로 `start/end`를 계산한다. 반복 문장은 다음 검색 위치를 기억한다. |
| 상태 | `QuestionAnalysisStatus` | `PROVEN`, `MENTIONED`, `MISSING`, `FABRICATED`가 있다. lowercase/uppercase를 정규화하고 `GOOD`, `NEEDS_IMPROVEMENT`, `RISK`도 호환한다. |
| 문항별 제한 | `AnalysisResultConstants.MAX_ANALYSES_PER_QUESTION` | 문항당 최대 3개다. 운영 워커 프롬프트는 대표 문장을 우선 선택한다. |
| MISSING | `Analysis.missingKeywordsJson` | `QuestionAnalysis`가 아니라 별도 JSON으로 저장한다. `sentence`, index가 필요 없어서 현 모델과 충돌하지 않는다. |
| 응답 DTO | `AnalysisResponse` | `keyStrengths`, `keyWeaknesses`, `missingKeywords`, `questions[].analyses[]`를 이미 반환한다. |
| Java 평가 Runner | `EvaluationAnalysisRunner` | CSV 한 행을 한 문항으로 평가하며 `analysis-eval` 프로필과 비용 확인 플래그가 필수다. 단, 실제 운영 Python 워커가 아니라 Java `AnalysisAiClient` 프롬프트를 사용한다. |

추가 확인 사항:

- `weaknessType`, `dimension`, `relatedRequirement` 필드는 현재 분석 enum/DTO/Entity에 없다.
- 요청서에서 언급한 `fixed-fewshot-cases.json`은 저장소에 없다. 고정 예시는 `fewshot-prompt-block.txt`이며, `curated-fewshot-cases.json`은 현재 빈 배열이다.
- 변경 전 Java 단일 패스 프롬프트는 좋은 문장을 `keyStrengths`로만 보내고 PROVEN을 `questionAnalyses`에서 금지했다.
- 변경 전 운영 Python 워커는 모든 답변 문항을 분석하도록 했지만, FABRICATED를 단순 과장 위험까지 포함하는 넓은 의미로 설명했다.
- 변경 전 운영 Python 워커의 Responses API 호출은 JSON 텍스트를 받은 뒤 Pydantic으로 파싱했을 뿐 OpenAI Structured Output을 요청하지 않았다.
- MISSING의 `keyword`가 현재 구조에서 누락된 JD 요건 역할을 한다. 별도 `relatedRequirement`를 추가하려면 API/DB 마이그레이션이 필요하므로 이번에는 기존 호환 구조를 유지했다.
- 직무 분류 리소스에서 `AI·개발·데이터 > 백엔드 개발`을 확인해 평가 CSV에 사용했다.

## 3. 발견한 구조적 문제와 변경

### PROVEN 유실

Spring API와 평가 Runner가 유효한 PROVEN도 무조건 제외하고 있었다. 이제 긍정적인 reason의 PROVEN은 저장하고, reason이 `근거 부족`, `보완 필요`처럼 상태와 모순될 때만 제외한다. PROVEN의 improvement는 기존 정책대로 빈 문자열로 정규화한다.

### MISSING 오탐과 표현 방식

MISSING을 `QuestionAnalysis`에 넣으려면 non-null sentence/index 모델을 바꿔야 한다. 기존 `missingKeywords` 구조를 유지하고 프론트에서 별도 `요건 누락` 영역을 추가했다.

모델이 이미 답변에 있는 DB·Git·장애 분석 요건을 간헐적으로 MISSING으로 반환하는 현상도 확인했다. 서버는 이제 JD 근거 검증 후 답변 전체의 정규화된 핵심 토큰과 충분히 겹치는 후보를 제거한다. Redis처럼 핵심 토큰이 실제로 없는 요건은 유지한다.

### FABRICATED 의미와 중복

FABRICATED는 단순 근거 부족이 아니라 같은 대상의 기간·인원·역할·수치가 직접 충돌하는 경우로 좁혔다. reason에 직접 충돌 근거가 없으면 서버가 계속 제거한다. 한 모순의 양쪽 문장을 두 번 보여 주지 않도록 동일 문항에서는 대표 FABRICATED 한 개만 저장한다.

### Structured Output

운영 Python 워커의 분석 모델을 `extra=forbid`, `Literal` status/source, 필수 배열, nullable improvement로 정의하고 Responses API에 strict JSON Schema를 전달한다. 임의 상태, 필드 누락, 추가 필드는 모델 응답 단계와 Pydantic 파싱 단계에서 모두 거부된다.

### 프론트 라벨

최종 사용자 라벨은 다음과 같이 정렬했다.

| 내부 상태 | 사용자 라벨 | 색상 |
|---|---|---|
| `proven` | 적절함 | 초록 |
| `mentioned` | 구체성 부족 | 분홍 |
| `fabricated` | 신뢰성 부족 | 빨강 |
| `missingKeywords` | 요건 누락 | 빨강 칩 + 누락 요건 목록 |

따라서 질문한 “프론트에서 mentioned → 구체성 부족, fabricated → 신뢰성 부족으로 교체해야 하느냐”는 맞다. 다만 라벨만 바꾸면 PROVEN 저장과 MISSING 미노출 문제가 남으므로 백엔드·워커 변경도 함께 필요했다.

## 4. 데모 데이터 설계

공고 원본은 `demo-job-posting.txt`, 복사용 문항과 답변은 `demo-cover-letter.md`에 있다.

| 상태 | 유도 데이터 | 판정 이유 |
|---|---|---|
| PROVEN | 복합 인덱스와 쿼리 재작성으로 응답 시간을 `1.8초 → 0.6초`로 개선 | 기술, 행동, 동일 조건의 전후 결과가 구체적이다. |
| MENTIONED | 여러 프로젝트에서 문제 원인을 분석하고 해결 방안을 적용해 역량을 길렀다는 주장 | 관련 요건은 언급하지만 어느 프로젝트에서 본인이 무엇을 했는지 없다. |
| MISSING | 공고의 `Redis 기반 캐시 또는 세션 관리 기능 설계 및 운영` | 세 답변 전체에 Redis·캐시·세션 저장소·인메모리 저장소 경험이 없다. |
| FABRICATED | 동일 JobBoard 프로젝트를 `시작부터 종료까지 1~3월, 다른 참여자 없음`과 `시작부터 종료까지 1~6월, 5명`으로 설명 | 동일 대상의 기간과 전체 참여 인원이 함께 성립할 수 없다. |

좋은 답변 하나, 보완 가능한 추상 답변 하나, 읽기 쉬운 직접 모순 하나로 분산했다. Redis 외 핵심 업무는 답변에서 실질적으로 다뤄 MISSING 화면이 과도하게 늘지 않도록 했다.

## 5. 실제 AI 반복 실행 결과

- 실행 모델: `gpt-4.1-mini`
- 호출 방식: 운영 Python `AnalysisOpenAiWorker.analyze`
- temperature: `0.2`
- 입력: `src/test/resources/evaluation/demo-day-cases.csv`의 공고·세 문항·답변을 하나의 운영 컨텍스트로 조립
- 비용 확인: 실행 명령에서 `CONFIRM_OPENAI_COST=true`를 명시적으로 검사

최종 3회 결과는 다음과 같다. 원시 모델 출력에는 이미 언급된 복합 JD 문구 또는 동일 모순의 반대쪽 문장이 추가되기도 했고, 표는 이번에 추가한 운영 서버 검증을 적용했을 때 최종 저장·노출되는 개수다. 실제 모델 호출과 Spring 저장 검증은 각각 실행했으며, RabbitMQ를 포함한 배포 환경 전체 E2E 호출은 수행하지 않았다.

| 실행 | PROVEN | MENTIONED | MISSING | FABRICATED | 총점 | 비고 |
|---|---:|---:|---:|---:|---:|---|
| 1 | 4 | 3 | 1 | 1 | 73 | 네 상태 모두 충족 |
| 2 | 5 | 2 | 1 | 1 | 72 | 네 상태 모두 충족 |
| 3 | 5 | 2 | 1 | 1 | 72 | 네 상태 모두 충족 |

OpenAI request ID와 세부 점수는 `demo-ai-repeat-results.csv`에 기록했다.

## 6. 테스트 결과

- Spring API 전체 테스트: `./gradlew test` 성공
- API 핵심 통합 테스트: 네 상태 저장·조회, PROVEN improvement 제거, substring/index, MISSING 답변 언급 필터, FABRICATED 문항별 중복 제거 성공
- Python 분석 변경 테스트: 33개 성공
  - strict JSON Schema 전달
  - nullable PROVEN improvement
  - 잘못된 status/추가 필드 거부
  - 직접 모순·MISSING·점수 규칙 프롬프트 포함
- 프론트 변경 파일 ESLint: 성공
- 프론트 전체 TypeScript 검사: 이번 변경과 무관한 기존 오류 8건 때문에 실패했다. 변경 파일에서는 ESLint 오류가 없다.
- Python 전체 `unittest discover`는 테스트들이 전역 `openai` stub을 공유해 import 순서에 따라 실패하는 기존 격리 문제가 있다. 이번 변경 대상인 분석 스키마·복구 흐름 테스트는 독립 실행으로 모두 성공했다.
- 예상 JSON 파싱과 두 CSV 파일의 행·헤더 검사: 성공

## 7. EvaluationAnalysisRunner 사용법

입력 파일:

`src/test/resources/evaluation/demo-day-cases.csv`

실행 예시:

```bash
./gradlew bootRun --args='--spring.profiles.active=analysis-eval --evaluation.analysis.enabled=true --evaluation.input=src/test/resources/evaluation/demo-day-cases.csv --evaluation.output=docs/demo-day/demo-evaluation-output.csv --evaluation.confirm-openai-cost=true'
```

환경에는 `OPENAI_API_KEY`가 필요하다. 이 Runner는 안전 플래그가 없거나 `prod` 프로필이면 실행을 거부한다. 또한 운영 Python 워커와 프롬프트 구현이 다르므로 회귀 비교용으로 사용하고, 배포 전 최종 안정성은 운영 워커로 확인한다.

## 8. 생성·수정 범위

### Spring API 저장소

- PROVEN 저장/조회 허용
- 실제 언급된 MISSING 후보 제거
- 동일 문항 FABRICATED 중복 제거
- Java 단일 패스 프롬프트와 고정 few-shot의 PROVEN 계약 정렬
- Evaluation Runner의 최종 검증 계약 정렬
- 데모 문서, 예상 JSON, 평가 CSV, 반복 실행 CSV 추가

### Python 분석 워커 저장소

- 직접 모순 우선 검사, MISSING source/전체 답변 검사, 점수 구간 규칙 보강
- strict Structured Output JSON Schema 적용
- 상태·source enum 및 필수 필드 Pydantic 검증 강화
- 프롬프트·스키마·호출 통합 테스트 추가

### 프론트 소스

- MENTIONED/FABRICATED 사용자 라벨과 색상 정렬
- `keyStrengths`, `keyWeaknesses`, `missingKeywords` 응답 타입 추가
- 결과 개요에 요건 누락 목록 추가
- 비어 있는 improvement 카드 숨김

프론트 디렉터리는 현재 Git 저장소가 아니어서 해당 변경은 API/워커와 별도로 버전 관리 위치를 확인해야 한다.

## 9. 내일 시연 절차

1. 데모 계정, 크레딧, API·워커·DB·RabbitMQ·OpenAI 상태와 DLQ를 확인한다.
2. `demo-job-posting.txt` 전체를 복사해 모의 공고를 생성한다.
3. 생성 결과에서 Redis 핵심 요건이 남았는지 확인한다.
4. `demo-cover-letter.md`의 방문자 시연용 문항 3개와 각 답변만 복사한다.
5. 분석을 한 번 실행하고 처리 중 중복 클릭하지 않는다.
6. 적절함 → 구체성 부족 → 요건 누락 → 신뢰성 부족 순서로 결과를 설명한다.
7. 한 라벨이 없으면 입력 원문 보존 여부를 먼저 확인하고 새 모의지원으로 한 번만 재시도한다.
8. 2분 안에 복구되지 않으면 사전 생성 결과 URL 또는 화면 캡처로 전환한다.

세부 장애 대응과 금지 사항은 `demo-operator-guide.md`를 따른다.
