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

sum(rate(fewshot_selection_count_total{mode="LOCAL_FALLBACK"}[10m]))
/
sum(rate(fewshot_selection_count_total[10m]))

sum(increase(fewshot_selection_count_total{mode="STATIC_FALLBACK"}[10m]))

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

- selection mode별 요청률, 10분 LOCAL fallback 비율, STATIC fallback 건수
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

운영 분석은 비동기 worker가 실행하므로 Nginx의 HTTP 트래픽 비율로 Few-shot 적용률을
제어하지 않습니다. Backend가 worker context를 만들 때 `taskId`의 SHA-256 버킷과
`worker-rollout-percentage`를 비교해 cohort를 고정합니다. 최초 context와 Few-shot 선택 결과는
task snapshot에 저장되므로 재시도에서도 동일하게 유지됩니다.

1. 평가·내부 인스턴스에서 대표 케이스와 cache miss·hit를 포함한 end-to-end 검증을 수행합니다.
2. 정식 서비스 오픈 전 운영 배포 환경에서는 worker rollout 5%로 readiness, 내부 요청과 지표
   수집만 확인합니다. 자연 트래픽 표본을 만들기 위한 대량 요청은 실행하지 않습니다.
3. 서비스 오픈 후 5% 실제 트래픽을 최소 1일과 하나의 일간 피크 구간 동안 관측합니다.
4. 필수 기준에 이상이 없으면 25% → 50% → 100% 순으로 확대합니다.
5. 25% 이후 각 단계에서도 최소 하나의 일간 피크 구간을 포함해 관측합니다.
6. 단계 변경 시 datasetVersion·설정값·배포 시각을 운영 기록에 남깁니다.

활성화 환경변수:

```text
ANALYSIS_FEW_SHOT_DYNAMIC_SELECTION_ENABLED=true
ANALYSIS_FEW_SHOT_WORKER_ROLLOUT_PERCENTAGE=5
ANALYSIS_FEW_SHOT_DATASET_VERSION=fewshot-pm-reviewed-20260914-v2
ANALYSIS_FEW_SHOT_FIXED_ENABLED=false
ANALYSIS_FEW_SHOT_CURATED_ENABLED=false
ANALYSIS_FEW_SHOT_REVIEWED_EVALUATION_ENABLED=false
ANALYSIS_FEW_SHOT_REVIEWED_PRODUCTION_ENABLED=true
ANALYSIS_FEW_SHOT_REVIEWED_PRODUCTION_RESOURCE=analysis/fewshot/reviewed-fewshot-cases-pm-20260914-v2.json
ANALYSIS_FEW_SHOT_TOP_K=5
ANALYSIS_FEW_SHOT_MIN_SIMILARITY=0.40
ANALYSIS_FEW_SHOT_MINIMUM_SELECTED_COUNT=2
```

운영에서는 `REVIEWED_EVALUATION`을 켜지 않고 PM 승인 리소스를 `REVIEWED_PRODUCTION`으로
로드합니다. 고정·curated 소스를 함께 켜면 평가에서 확정한 5건 외 후보가 섞이므로 초기 canary에서는
둘 다 비활성화합니다.

## 중단 판단 기준

초기 canary에서는 다음 중 하나면 확대를 멈추고 원인을 확인합니다.

- 10분간 LOCAL_FALLBACK 비율이 25% 초과
- 최소 20건 표본에서 10분간 STATIC_FALLBACK이 1건 이상 발생
- 10분간 Cohere 실패가 Few-shot 선택 요청의 5% 초과
- 10분간 최소 20건 표본에서 Few-shot 선택 P95가 2초 초과
- 회귀 샘플에서 unsupported fact 또는 false positive 증가

트래픽이 적으면 짧은 비율만으로 판단하지 않고 최소 20건 이상의 표본을 함께 확인합니다.
분석 전체 P95와 시간당 Cohere 호출량은 현재 Few-shot 전용 운영 baseline이 없거나 분리가
불가능하므로 기록만 합니다. baseline을 수립하기 전에는 25% 확대를 차단하는 필수 조건으로
사용하지 않습니다.

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
