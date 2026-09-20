# 동적 Few-shot 단계적 운영 가이드

## 운영 전제

- 기본값 `ANALYSIS_FEW_SHOT_DYNAMIC_SELECTION_ENABLED=false`를 유지합니다.
- 권장 검색값은 `top-k=5`, `min-similarity=0.40`, `minimum-selected-count=2`입니다.
- 승인 데이터셋 버전을 명시하고, 후보 변경 시 새 버전으로 배포합니다.
- 개인정보 정책은 `docs/fewshot-privacy-masking.md`를 따릅니다.

## 관측 지표

Prometheus endpoint는 관리 포트의 `/actuator/prometheus`입니다.

| 지표 | 태그 | 용도 |
| --- | --- | --- |
| `fewshot_selection_count_total` | `mode`, `cache_hit` | EMBEDDING·LOCAL_FALLBACK·STATIC_FALLBACK 비율 |
| `fewshot_selection_duration_seconds` | `mode`, `cache_hit` | 검색 평균·P95 지연 |
| `fewshot_selection_selected_candidates_count/_sum` | `mode`, `cache_hit` | 선택 후보 수 분포 |
| `fewshot_cohere_logical_calls_total` | 없음 | Few-shot이 발생시킨 Cohere 논리 호출량 |
| `fewshot_cohere_failure_count_total` | `reason` | Cohere 검색 실패 유형 |
| `fewshot_cache_events_total` | `cache`, `outcome` | 캐시별 hit·hit_after_claim·miss·expired·evicted 횟수 |

원문 JD·답변·검색 텍스트·embedding은 지표 태그나 로그에 넣지 않습니다. `reason`은 정해진 예외
분류만 허용하고 그 외 값은 `Other`로 묶어 tag cardinality 증가를 방지합니다.

대표 PromQL:

```promql
sum(rate(fewshot_selection_count_total[10m])) by (mode)

sum(rate(fewshot_selection_count_total{mode=~"LOCAL_FALLBACK|STATIC_FALLBACK"}[10m]))
/
sum(rate(fewshot_selection_count_total[10m]))

histogram_quantile(
  0.95,
  sum(rate(fewshot_selection_duration_seconds_bucket[10m])) by (le)
)

sum(rate(fewshot_cohere_failure_count_total[10m])) by (reason)

sum(increase(fewshot_cohere_logical_calls_total[1h]))
```

## Grafana Cloud 대시보드

저장소의 `ops/observability/grafana/fewshot-dashboard.json`을 Grafana Cloud의
**Dashboards > New > Import**에서 업로드하고, 가져오기 화면에서 운영 Prometheus datasource를
선택합니다. 이 대시보드는 다음 항목을 한 화면에서 확인합니다.

- selection mode별 요청률과 10분 fallback 비율
- selection P95와 mode별 지연 추이
- Cohere 실패 reason과 시간당 논리 호출량
- 평균 선택 후보 수
- 캐시별 hit 비율과 expired·evicted 건수

패널이 `No data`이면 먼저 Grafana **Explore**에서 `fewshot_selection_count_total`을 조회합니다.
지표 자체가 없으면 해당 인스턴스에서 동적 Few-shot 요청이 발생했는지와 Grafana Cloud로
`/actuator/prometheus`가 수집되고 있는지를 확인합니다. 대시보드의 datasource 변수는 Grafana
Cloud에 등록된 Prometheus datasource를 사용하므로 별도 모니터링 시스템을 추가로 띄우지 않습니다.

Grafana-managed alert rule은 dashboard JSON과 별도로 등록합니다. 경보별 PromQL과
평가 주기·Pending·No Data 정책은 `docs/fewshot-alerting.md`를 따릅니다.

## 단계적 활성화

애플리케이션 feature flag는 boolean이므로 트래픽 비율은 배포 플랫폼의 인스턴스 또는 라우팅
단위로 나눕니다.

1. 평가·내부 인스턴스에서만 활성화하고 최소 1일 관측합니다.
2. 운영 canary 인스턴스 5%에서 활성화합니다.
3. 이상이 없으면 25% → 50% → 100% 순으로 확대합니다.
4. 각 단계에서 최소 하나의 일간 피크 구간을 포함해 관측합니다.
5. 단계 변경 시 datasetVersion·설정값·배포 시각을 운영 기록에 남깁니다.

활성화 환경변수:

```text
ANALYSIS_FEW_SHOT_DYNAMIC_SELECTION_ENABLED=true
ANALYSIS_FEW_SHOT_DATASET_VERSION=fewshot-pm-reviewed-20260914-v2
ANALYSIS_FEW_SHOT_TOP_K=5
ANALYSIS_FEW_SHOT_MIN_SIMILARITY=0.40
ANALYSIS_FEW_SHOT_MINIMUM_SELECTED_COUNT=2
```

## 중단 판단 기준

초기 canary에서는 다음 중 하나면 확대를 멈추고 원인을 확인합니다.

- 10분간 LOCAL_FALLBACK + STATIC_FALLBACK 비율이 10% 초과
- 10분간 Cohere 실패가 Few-shot 선택 요청의 5% 초과
- Few-shot 선택 P95가 2초 초과 또는 기존 기준 대비 30% 이상 증가
- 분석 전체 P95가 기존 기준 대비 20% 이상 증가
- 시간당 Cohere 호출량이나 OpenAI 입력 토큰이 예상 범위를 20% 이상 초과
- 회귀 샘플에서 unsupported fact 또는 false positive 증가

트래픽이 적으면 짧은 비율만으로 판단하지 않고 최소 20건 이상의 표본을 함께 확인합니다.

## 즉시 복귀

1. 활성 인스턴스의 `ANALYSIS_FEW_SHOT_DYNAMIC_SELECTION_ENABLED=false`로 재배포합니다.
2. 선택 모드가 STATIC으로 돌아왔는지 로그와 지표로 확인합니다.
3. fallback이 아니라 feature flag 비활성으로 복귀했는지 확인합니다.
4. datasetVersion, 오류 시각, 실패 reason, 지연과 호출량을 장애 기록에 남깁니다.
5. 승인 데이터나 캐시를 삭제하지 않습니다. 원인 수정 후 평가 환경에서 재검증합니다.

flag를 끄면 기존 정적 Few-shot 프롬프트로 돌아가며 API 응답·DB 스키마는 바뀌지 않습니다.

## 활성화 승인 조건

- 승인 후보 데이터와 개인정보 검수 완료
- STATIC/DYNAMIC 품질 비교와 튜닝 회귀 평가 완료
- fallback·Cohere 실패·검색 P95·호출량 대시보드 확인 가능
- canary 단계에서 중단 기준 미충족
- 장애 시 flag 비활성 재배포 절차를 담당자가 실행 가능
