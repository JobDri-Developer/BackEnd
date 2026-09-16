# Few-shot 평가 메타데이터

후속 이슈 2번의 선택 이력 기록입니다. 운영 API·DB·기존 평가 CSV 컬럼은 변경하지 않습니다.
`EvaluationAnalysisRunner`가 사용하는 배치 서비스에서 실행마다 별도 JSONL 파일을 생성합니다.

## 출력 및 연결

평가 출력이 `evaluation_ai_results.csv`라면 같은 폴더에
`evaluation_ai_results.csv.fewshot.<runId>.jsonl`이 생성됩니다.
정확한 경로는 배치 시작 로그의 `Few-shot evaluation metadata output`에 표시됩니다.
실행별 새 파일이므로 이전 메타데이터를 덮어쓰지 않습니다.

- 한 줄은 입력 CSV의 데이터 행 하나에 대응합니다.
- `rowIndex`: 헤더를 제외한 1부터 시작하는 행 번호.
- `caseId`: 평가 입력의 ID. 중복 ID는 `rowIndex`로 구분합니다.
- `runId`: 한 실행 내에서 동일한 UUID.
- `outcome`: SUCCESS 또는 FAILED.
- `metadataStatus`: RECORDED 또는 UNAVAILABLE.
- `captureStage`: SELECTION_OBSERVED 또는 UNAVAILABLE.
- `selections`: 이 행에서 관측한 선택 스냅샷 배열.
- `schemaVersion=1`, `recordedAt`: sidecar 형식 버전 및 UTC 기록 시각.

행 처리 직후 flush하므로 뒤 행에서 실패해도 앞 행의 메타데이터는 남습니다.
비정상 종료 시 sidecar는 부분 결과일 수 있으며, CSV 생성까지 완료되었다는 뜻은 아닙니다.
sidecar 저장 실패는 평가 실행 실패로 전파됩니다.

## 선택 스냅샷

| selectionMode | 의미 | scoreType |
| --- | --- | --- |
| STATIC | 동적 선택 비활성, 기존 정적 전체 예시 | NONE |
| EMBEDDING | 임베딩 검색 후보 사용(캐시 반환 포함) | COSINE_SIMILARITY |
| LOCAL_FALLBACK | 로컬 기준으로 선택한 후보 사용 | LOCAL_HEURISTIC |
| STATIC_FALLBACK | 빈 결과·검색 예외 등으로 정적 전체 예시 복귀 | NONE |
| NOT_APPLIED | two-pass 경로에는 Few-shot 미적용 | NONE |

- `selectedCases`: 실제 조립한 예시의 ID, source, score, 후보 datasetVersion.
- `topScore/bottomScore/avgScore`: 최종 선택 후보의 최대·최소·평균 점수.
  프롬프트 순서와 최대·최소 점수 순서는 다를 수 있습니다.
- `datasetVersion/minSimilarity/topK/minimumSelectedCount`: 실행의 선택 설정.
  topK는 설정한 요청 개수이며 실제 개수는 selectedCases 배열 길이입니다.
- `cohereApiCallCount`: 해당 프롬프트 선택 중 발생한 논리적 Cohere embedding 호출 수.
  캐시 적중 시 0이며, SDK/HTTP 계층의 내부 재전송 횟수와는 구분합니다.
- `reason`: 정적 선택·fallback·미적용 사유 코드. 예외 메시지 원문은 넣지 않습니다.

정적 예시 ID는 기존 로더와 같은 `FS-FIXED-1..N`입니다.
정적 후보의 datasetVersion은 `static-resource`로 표시하며, 검색 설정의 datasetVersion과 구분합니다.
정적 선택은 유사도 계산이 없으므로 후보 점수와 점수 통계는 **0이 아니라 null**입니다.
LOCAL_HEURISTIC 점수는 코사인 유사도와 섞어 분포를 비교하면 안 됩니다.

## 실제 호출과의 관계

프롬프트 조립 시 검색을 한 번 수행하고 그 선택 결과를 기록합니다.
메타데이터 수집을 위한 추가 Cohere/OpenAI 호출은 없습니다.
기록은 외부 AI 호출 전에 전달되므로, 시간 초과나 응답 검증 실패 행에도 선택 이력이 남습니다.
따라서 RECORDED는 **프롬프트 선택을 관측했다는 뜻**이며, API 호출 성공이나 토큰 소비를 보장하지 않습니다.

single-pass는 한 선택을, hybrid-exact는 single-pass 하위 호출의 선택을 기록합니다.
two-pass는 NOT_APPLIED를 기록합니다.
선택 전에 실패했거나 메타데이터를 지원하지 않는 다른 generator는 UNAVAILABLE로 기록하며
STATIC으로 추정하지 않습니다.

선택 스냅샷에는 자소서 원문, JD, 프롬프트 본문, 임베딩 벡터를 넣지 않습니다.
기존 평가 CSV의 원문 보존 동작은 바꾸지 않았으므로 CSV 접근 권한은 기존대로 관리해야 합니다.

## 분석 사용량

평가 CSV의 `candidateInputTokens`, `candidateOutputTokens`, `finalInputTokens`,
`finalOutputTokens`, `totalInputTokens`, `totalOutputTokens`에는 OpenAI 응답 usage를 기록합니다.
single-pass 사용량은 final 컬럼에 기록되고 candidate 컬럼은 비어 있습니다. two-pass는 후보와
최종 검토를 나눠 기록하며, 선택적으로 실행되는 recheck 사용량은 final에 합산합니다.

## 검증

`./gradlew test --tests '*AnalysisAiClientTest' --tests '*Evaluation*' --tests '*fewshot.*' --tests '*FewShotMetadataPromptTest' --tests '*FewShotPromptProviderTest'`

192개 통과(실패·건너뜀 0). 선택 이력과 프롬프트의 일치, 로컬 점수 구분,
정적 fallback, 모드별 실패 전 기록, 실패 행 보존, 중복 caseId 행 구분,
기존 CSV 컬럼 유지 및 실행별 파일 분리를 확인했습니다. 외부 AI 호출은 수행하지 않았습니다.
