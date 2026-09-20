# 동적 Few-shot Grafana Cloud 경보 설정

## 적용 방식

Grafana Cloud에서 **Alerts & IRM > Alert rules > New alert rule**로 이동해 Grafana-managed
alert rule을 생성합니다. datasource는 Few-shot 대시보드에서 사용한 운영 Prometheus를
선택합니다.

공통 설정:

| 항목 | 값 |
| --- | --- |
| Folder | `Jobdri Production` 또는 운영 경보용 폴더 |
| Evaluation group | `fewshot-1m` |
| Evaluation interval | `1m` |
| No Data | Normal |
| Error/Timeout | Keep Last State |
| Label | `service=jobdri-api`, `component=fewshot`, `environment=production`, `severity=warning` |

동적 Few-shot이 꺼져 있거나 요청이 없는 동안에는 지표가 없을 수 있으므로 No Data를 장애로
처리하지 않습니다. 알림 contact point와 notification policy는 기존 운영 알림 채널을
사용합니다.

## 1. Local fallback 비율 경보

- 이름: `JobDri Few-shot local fallback ratio high`
- 심각도: `warning`
- Pending period: `5m`
- 복구 기준: 아래 조건이 더 이상 성립하지 않을 때

```promql
(
  sum(increase(fewshot_selection_count_total{mode="LOCAL_FALLBACK"}[10m]))
  /
  clamp_min(sum(increase(fewshot_selection_count_total[10m])), 1)
) > 0.25
and
sum(increase(fewshot_selection_count_total[10m])) >= 20
```

10분간 selection이 20건 이상이면서 local fallback 비율이 25%를 초과한 상태가
5분간 지속되면 발동합니다. 내부 smoke 기준선은 15%입니다.

추가 라벨: `signal=local_fallback_ratio`

## 2. Static fallback 발생 경보

- 이름: `JobDri Few-shot static fallback detected`
- 심각도: `warning`
- Pending period: `3m`
- 복구 기준: 10분 구간의 static fallback이 0건으로 돌아올 때

```promql
sum(increase(fewshot_selection_count_total{mode="STATIC_FALLBACK"}[10m])) > 0
and
sum(increase(fewshot_selection_count_total[10m])) >= 20
```

`STATIC_FALLBACK`은 동적·로컬 선택이 모두 빈 결과를 반환한 경우이므로 local fallback과
분리해 관측합니다.

추가 라벨: `signal=static_fallback`

## 3. Cohere 실패 비율 경보

- 이름: `JobDri Few-shot Cohere failure ratio high`
- 심각도: `warning`
- Pending period: `3m`
- 복구 기준: 아래 조건이 더 이상 성립하지 않을 때

```promql
(
  sum(increase(fewshot_cohere_failure_count_total[10m]))
  /
  clamp_min(sum(increase(fewshot_selection_count_total[10m])), 1)
) > 0.05
and
sum(increase(fewshot_selection_count_total[10m])) >= 20
```

10분간 selection이 20건 이상이면서 Cohere 선택 실패가 selection 요청의 5%를 초과한 상태가
3분간 지속되면 발동합니다.

추가 라벨: `signal=cohere_failure`, `dependency=cohere`

## 4. Few-shot 선택 P95 지연 경보

- 이름: `JobDri Few-shot selection P95 latency high`
- 심각도: `warning`
- Pending period: `5m`
- 복구 기준: 아래 조건이 더 이상 성립하지 않을 때

```promql
histogram_quantile(
  0.95,
  sum(rate(fewshot_selection_duration_seconds_bucket[10m])) by (le)
) > 2
and
sum(increase(fewshot_selection_count_total[10m])) >= 20
```

10분 구간의 selection P95가 2초를 초과한 상태가 5분간 지속되면 발동합니다.

추가 라벨: `signal=selection_latency`

Discord를 직접 연결한 경우 각 규칙의 **Configure notifications > Select contact point**에서
`jobdri-discord-alerts`를 선택합니다. 네 규칙이 같은 contact point를 공유해도 됩니다.

## 등록 전 확인

각 쿼리를 Grafana **Explore**에서 먼저 실행합니다.

1. 시간 범위를 최근 Few-shot 요청이 포함된 구간으로 설정합니다.
2. 운영 Prometheus datasource를 선택합니다.
3. 쿼리가 오류 없이 실행되는지 확인합니다.
4. 테스트 트래픽이 20건 미만이면 표본 조건 때문에 결과가 없는 것이 정상입니다.
5. Alert rule 생성 후 **Preview alert rule condition**으로 상태를 확인합니다.

대시보드의 `$__rate_interval` 같은 template variable은 alert rule에서 사용하지 않고, 위 쿼리처럼
고정된 `[10m]` 범위를 사용합니다.

## 경보 발생 시 대응

1. `JobDri / Dynamic Few-shot` 대시보드에서 동일 시간대의 selection mode, Cohere 실패 reason,
   P95, 호출량을 확인합니다.
2. 최근 배포의 datasetVersion과 `top-k`, `min-similarity`, `minimum-selected-count` 변경 여부를
   확인합니다.
3. Cohere 실패면 API 상태와 자격 증명·할당량을 확인합니다. 로그에 원문 JD나 답변을 남기지
   않습니다.
4. canary 단계라면 트래픽 확대를 즉시 중단합니다.
5. 영향이 지속되면 `ANALYSIS_FEW_SHOT_DYNAMIC_SELECTION_ENABLED=false`로 재배포합니다.
6. 복귀 후 selection이 기존 정적 경로로 처리되는지 확인하고 발생 시각, datasetVersion,
   실패 reason, 지연을 장애 기록에 남깁니다.

## 초기에는 경보로 만들지 않는 항목

- 시간당 Cohere 호출량: 실제 운영 기준선이 없어 대시보드로 먼저 관찰합니다.
- 캐시 eviction: 트래픽과 cache limit에 따라 정상적으로 발생할 수 있어 기준선 확보 후 경보를
  추가합니다.
- OpenAI 토큰 및 분석 전체 P95: 현재 Few-shot 전용 운영 지표만으로 정확히 분리할 수 없으므로
  기존 분석 서비스 지표와 함께 판단합니다.

최소 하루의 내부·canary 관측값이 쌓이면 호출량, eviction, 전체 분석 지연의 기준선을 정하고
경보 범위를 확장합니다.
