# PM 검수 Few-shot 적재

기존 `fix/fewshot-selection-safety-cache` 작업 브랜치에 로더 보강을 추가했습니다.
기존 dev에 후보 및 질의 임베딩 캐시가 있으므로 캐시는 중복 구현하지 않았습니다.

## 변경

- CSV의 주요 업무·자격요건·우대사항 중 하나 이상 있으면 JD를 허용합니다.
  세 항목이 모두 빈 사례는 제외합니다. 누락된 원문 정보를 생성하지 않습니다.
- jobTitle을 보존하고, 없는 기존 CSV만 jobCategorySmall로 대체합니다.
- 우대사항을 예시 프롬프트에 포함합니다.
- approvedAnalysisJson은 단일 JSON 객체여야 합니다. 잘못된 행만 제외하고 다음 행을 읽습니다.
  이 검사는 JSON 문법과 최상위 객체 검사이며 분석 내용의 의미 검증은 아닙니다.
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
