# PM 검수 Few-shot 적재

기존 `fix/fewshot-selection-safety-cache` 작업 브랜치에 로더 보강을 추가했습니다.
기존 dev에 후보 및 질의 임베딩 캐시가 있으므로 캐시는 중복 구현하지 않았습니다.

## 변경

- CSV의 주요 업무·자격요건·우대사항 중 하나 이상 있으면 JD를 허용합니다.
  세 항목이 모두 빈 사례는 제외합니다. 누락된 원문 정보를 생성하지 않습니다.
- jobTitle을 보존하고, 없는 기존 CSV만 jobCategorySmall로 대체합니다.
- 우대사항을 예시 프롬프트에 포함합니다.
- approvedAnalysisJson은 단일 JSON 객체여야 합니다. 잘못된 행만 제외하고 다음 행을 읽습니다.
  검수 CSV와 JSON 리소스 모두 아래 부분 분석 계약을 검증합니다. 분석 의미의 최종 승인은 PM에게 있습니다.
- 활성·승인·caseId·문항·비식별 답변 검증을 유지합니다.

## 로컬 평가 설정

검수 후보 CSV는 평가 입력(holdout)과 분리해야 합니다.
후보 JSON과 CSV는 동일 데이터이므로 동시에 로딩하지 않습니다.

```properties
analysis.few-shot.dynamic-selection-enabled=true
analysis.few-shot.dataset-version=fewshot-pm-reviewed-20260914-v2
analysis.few-shot.source.reviewed-evaluation-enabled=true
analysis.few-shot.reviewed-evaluation-resource=
analysis.few-shot.reviewed-evaluation-csv-path=/absolute/path/fewshot_candidates_approved.csv
```

운영 기본 활성화 설정은 변경하지 않았습니다. Python 워커 연결과 실제 API 비용이
발생하는 평가는 이 변경에 포함하지 않습니다.

## 검수 확인

사용자가 PM에게 재확인한 내용: FS-02-S1의 fabricated 변경은 의도된 것이며,
우선순위 1이 최상위입니다. FS-02-S1은 상태 변경 의도 때문에 보류한 것이 아니라
최종 판정 이유와 정책 정합성 보완이 남아 활성 예시에서 제외한 상태입니다.

## 검증

`./gradlew test --tests '*fewshot.*'`: 27개 성공.
선택 서비스의 기존 캐시·입력 제외 회귀 테스트와 CSV 로더의 선택 JD 필드,
직무명 호환, 우대사항 보존, 잘못된 JSON 행 이후 정상 행 적재를 확인했습니다.

## 2026-09-15 승인 데이터 연결 및 검증 보강

- 기존 변환본 `fewshot-pm-reviewed-20260914-v2`의 FS-02·03·05·08·09, 총 5개를
  `analysis/fewshot/reviewed-fewshot-cases-pm-20260914-v2.json`에 반영했습니다.
  새 승인을 만들어낸 것이 아니라 기존 승인·비식별 통과 데이터만 연결했습니다.
  동일한 데이터이므로 버전은 v2를 유지합니다. 후속 데이터 변경 시 새 버전을 사용합니다.
- PM 우선순위 1이 최상위라는 확인은 변환본의 `10-rank` 값으로 이미 반영되어 있습니다.
  검색기는 큰 priority를 우선하므로 추가로 역전하지 않습니다.
- 기존 점수는 예시에 넣지 않았으며 보류 사례·문장도 다시 활성화하지 않았습니다.
- 데이터 및 프롬프트의 이메일·국내 휴대전화·HTTP URL 패턴 검사에서 일치 항목이 없었습니다.
  이는 이름·소속·경험 조합을 통한 재식별 위험까지 보장하는 개인정보 정책 검증은 아닙니다.
  원본 승인 범위는 유지하며 5번 이슈에서 정책을 별도로 확정해야 합니다.

### 부분 분석 계약

- `keyStrengths`, `missingKeywords`, `questionAnalyses`: 필수 배열. 빈 배열 허용.
- 강점 항목: 비어 있지 않은 문자열 `title`, `quote`.
- 누락 항목: 비어 있지 않은 `keyword`, 출처는 `mainTask|qualification|preference`.
- 문장 항목: 양의 정수 `questionId`, 비어 있지 않은 `sentence`, `reason`,
  `status=proven|mentioned|fabricated`, `improvement`는 null 또는 비어 있지 않은 문자열.
- 점수·feedback은 부분 Few-shot 계약의 필수 필드가 아닙니다.
- CSV의 필수 헤더, 중복 헤더, 열 개수, 닫히지 않은 따옴표도 검사합니다.
  구조상 행 경계를 신뢰할 수 없는 CSV는 파일 단위로 제외합니다.
- JSON 리소스 내부 source로 승인 데이터 검증을 우회할 수 없도록 로딩 경로의 source를 적용합니다.
- 검증 통과 후 ID와 정규화 입력을 등록합니다. 같은 입력의 첫 유효 후보만 유지합니다.
  기존 검색 단계와 동일한 NFKC·공백·대소문자 정규화 및 SHA-256을 재사용합니다.
  입력 지문은 기존 호환성을 위해 주요 업무·자격요건·문항·답변을 기준으로 하며
  우대사항만 다른 입력은 같은 입력으로 보수적으로 취급합니다.
- 파싱 오류 로그에는 답변이 포함될 수 있는 예외 메시지를 남기지 않습니다.

### 평가 프로필

`analysis-eval,fewshot-pm-eval`을 명시적으로 함께 선택하면 검수 후보 5개만 사용합니다.
이 전용 프로필은 `analysis.mode=single-pass`로 지정합니다. two-pass에는 Few-shot이 적용되지 않습니다.
운영 기본 프로필이나 기본 feature flag는 변경하지 않았습니다.

STATIC 비교 시 동일 프로필에서 `analysis.few-shot.dynamic-selection-enabled=false`만 덮어씁니다.
STATIC 또는 동적 실패 시 기존 정적 전체 블록이 사용되므로, 사용 후보 추적과 fallback 구분은
후속 2번 메타데이터 기록 작업이 완료된 뒤 품질 평가에서 확인해야 합니다.
holdout은 후보 원본 및 단순 비식별 변형과 분리하세요. 해시 검사는 유사문·비식별 전후 변형까지
탐지하지 못하므로 데이터 분할 검토를 대체하지 않습니다.

이번 작업에서는 외부 AI 호출, 운영 활성화, 실제 품질 비교 평가를 수행하지 않았습니다.

검증: `./gradlew test --tests '*fewshot.*' --tests '*AnalysisPromptBuilderTest' --tests '*FewShotPromptProviderTest' --tests '*Evaluation*'`
149개 통과(실패·건너뜀 0). `git diff --check` 통과.
