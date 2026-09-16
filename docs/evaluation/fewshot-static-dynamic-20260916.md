# STATIC/DYNAMIC Few-shot 비교 평가 (2026-09-16)

## 결론

DYNAMIC은 20건 중 12건 개선, 4건 동일, 4건 악화였습니다. Judge의 평균 overall usefulness는
3.70에서 4.15로 0.45점(5점 척도) 상승했습니다. 품질 개선 신호는 분명하지만,
분석 평균 지연이 5,210ms에서 5,731ms로 약 10.0% 증가했고 4건이 악화됐으므로
운영 활성화가 아니라 4번 임계값·top-k 튜닝으로 진행합니다.

이번 결과만으로 기본 feature flag를 켜지 않습니다. 동일 조건 반복 평가와 악화 사례 분석 후
최종 운영 적용 여부를 결정합니다.

## 조건

- 평가일: 2026-09-16 (Asia/Seoul)
- holdout: `evaluation_cases_검수(2).csv`, EV-01~EV-20 총 20건
- 후보: `fewshot-pm-reviewed-20260914-v2`, 승인 후보 5건
- 후보와 holdout의 normalized input hash 정확 일치: 0건
- 분석 모델: `gpt-4o-mini`
- 분석 temperature: 0.2
- Judge 모델: `gpt-4o-mini`
- Judge temperature: 0.0
- 분석 모드: single-pass
- DYNAMIC 설정: minSimilarity=-1.0, topK=5, minimumSelectedCount=1
- STATIC/DYNAMIC 외 입력·모델·프롬프트 코드·Judge 조건 동일
- 분석 및 Judge: 양쪽 각 20/20 성공

LLM 출력은 비결정적이므로 이 평가는 단일 실행 비교입니다. 같은 조건이어도 재실행 결과가
달라질 수 있습니다.

## 요약

| 항목 | STATIC | DYNAMIC | 변화 |
| --- | ---: | ---: | ---: |
| Overall usefulness | 3.70 | 4.15 | +0.45 |
| Relevance | 4.00 | 4.50 | +0.50 |
| Problem validity | 3.39 | 4.13 | +0.74 |
| Reason correctness | 3.92 | 4.35 | +0.43 |
| Context awareness | 3.32 | 4.13 | +0.81 |
| Faithfulness | 4.13 | 4.55 | +0.42 |
| Usability | 4.08 | 4.35 | +0.27 |
| Missing keyword precision | 3.55 | 4.55 | +1.00 |
| Missing keyword coverage | 3.65 | 4.55 | +0.90 |
| 평균 분석 지연 | 5,210ms | 5,731ms | +521ms (+10.0%) |
| P95 분석 지연 | 7,454ms | 8,273ms | +819ms (+11.0%) |
| Judge 평균 지연 | 4,584ms | 4,122ms | -462ms |
| Fatal error rate | 0% | 0% | 동일 |
| Unsupported fact rate | 0% | 0% | 동일 |
| False positive analysis rate | 0% | 0% | 동일 |

Sentence type consistency는 4.11에서 4.50으로 상승했습니다. status별 정답률을 직접 계산할
별도 정답 라벨은 holdout에 없으므로, 이 값과 Judge의 problem validity를 status 품질의 대리
지표로 사용했습니다.

## 케이스 분류

분류 기준은 케이스별 Judge overall usefulness의 DYNAMIC-STATIC 차이입니다.

- 개선 12건: EV-01, EV-03, EV-04, EV-05, EV-08, EV-09, EV-10, EV-11,
  EV-12, EV-13, EV-16, EV-20
- 동일 4건: EV-06, EV-07, EV-17, EV-19
- 악화 4건: EV-02, EV-14, EV-15, EV-18

가장 큰 개선은 EV-09·EV-11(+2), 가장 큰 악화는 EV-14(-2)였습니다.
EV-18은 STATIC의 `MISSED_MISSING_KEYWORD`가 DYNAMIC에서 `MISSED_ANALYSIS`로 바뀌었고,
DYNAMIC의 overall usefulness가 2점으로 내려갔습니다. 4번 튜닝에서 EV-02·14·15·18의
선택 후보와 similarity를 우선 분석합니다.

## 선택과 비용·호출량

- DYNAMIC 20건 모두 `EMBEDDING`, fallback 0건(0%).
- 전체 선택 similarity 평균: 0.4140
- 케이스별 top similarity 평균: 0.4778
- 케이스별 bottom similarity 평균: 0.3503
- minSimilarity=-1.0, topK=5라서 모든 케이스에 후보 5건이 들어갔습니다.
- Cohere HTTP 호출 수: STATIC 0회, DYNAMIC 21회로 계산됩니다.
  첫 케이스의 query 1회+document batch 1회, 이후 query 19회이며 document embedding은
  dataset cache를 재사용했습니다. 현재 sidecar가 직접 센 값은 아니므로 4번 전에 호출 카운터
  계측을 추가하는 것이 안전합니다.
- Judge 토큰: STATIC input 116,728 / output 9,226,
  DYNAMIC input 115,906 / output 8,278.
- 분석 OpenAI 토큰은 기존 분석 adapter가 usage를 결과에 전달하지 않아 이번 실행에서
  수집하지 못했습니다. 기존 CSV의 token 컬럼도 비어 있습니다. 비용 비교 완료 전
  분석 usage 계측을 추가해야 합니다.

## 실행 중 발견하고 수정한 문제

1. Cohere v2 응답에는 `id`, `meta`, `response_type`, `texts`가 포함됩니다. DTO가 알 수 없는
   필드를 거부해 모든 동적 선택이 LOCAL_FALLBACK 되던 문제를 수정했습니다.
2. single-pass 결과의 `sanitizedCandidateResponseJson` 값이 JSON `null`일 때 Judge 입력 생성이
   NullPointerException으로 실패하던 문제를 수정했습니다.

각 문제에 실제 응답 형태 및 single-pass null 회귀 테스트를 추가했습니다.

## 산출물

로컬 원본 결과는 `build/evaluation/fewshot-comparison-20260916/full/`에 있습니다.

- `static-analysis.csv`, `dynamic-analysis.csv`
- 각 분석 CSV의 `*.fewshot.<runId>.jsonl` 선택 메타데이터
- `static-judge.csv`, `dynamic-judge.csv`
- `judge-comparison.csv`
- 각 실행 로그

`build/`는 Git 추적 대상이 아닙니다. 결과 재현이 필요하면 동일 holdout과 설정으로 다시
실행하거나, 개인정보 보관 정책을 확인한 후 별도 안전한 저장소에 보관해야 합니다.

## 다음 결정

3번의 품질 비교는 완료됐고 결과는 DYNAMIC의 튜닝 진행을 지지합니다. 다만 완료 조건의 비용
항목 중 분석 OpenAI 토큰 실측이 남아 있으므로, 이 계측을 추가하기 전에는 3번을 완전 완료로
닫지 않습니다. 다음 작업은 분석 usage와 Cohere 호출 수 계측을 추가한 뒤 4번 임계값·top-k
튜닝 실험을 설계하는 것입니다.
