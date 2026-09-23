# 동적 Few-shot 운영 5% Canary 관측 기록

## 배포 기준선

| 항목 | 값 |
| --- | --- |
| 상태 | 정식 서비스 오픈 전 사전 검증 중 |
| 관측 시작 | 2026-09-21 17:20 KST |
| Backend 이미지 | `ghcr.io/jobdri-developer/backend@sha256:d4019fe79e9d53971fa8194bdfd43579410dca689b46abe2a8db3751ad9804a9` |
| 활성 profile | `prod,fewshot-canary` |
| datasetVersion | `fewshot-pm-reviewed-20260914-v2` |
| worker rollout | 5% |
| topK | 5 |
| minSimilarity | 0.40 |
| minimumSelectedCount | 2 |
| 승인 후보 | `FS-02`, `FS-03`, `FS-05`, `FS-08`, `FS-09` |

기동 로그에서 `fewshot-canary readiness validated`와 애플리케이션 시작 완료를 확인했다.
검증 시점의 유효 후보는 5건이며 모두 `REVIEWED_PRODUCTION` 데이터다.

## 배포 중 확인된 사항

- 서버의 기존 `latest` 태그가 2026-09-07 이미지에 머물러 있어 최초 확인 시 카나리 클래스와
  profile 리소스가 없었다.
- 배포 워크플로가 빌드 결과의 immutable digest를 Compose의 `IMAGE_REF`로 전달하도록 변경했다.
  위 digest는 2026-09-21 배포의 GitHub Actions Docker build record에서 확인한 값이다.
- 카나리 활성화 전 발생한 `DatasourceNoData` 알림은 실제 `STATIC_FALLBACK`이 아니라
  시계열 부재를 경보로 처리한 설정 문제였다.
- Few-shot 경보는 `No Data=Normal`, `Error/Timeout=Keep Last State`를 사용한다.
- 경보 라벨은 `severity=warning`으로 통일한다.

## 오픈 전 사전 검증

현재 정식 서비스 운영 전이므로 자연 트래픽의 일간 피크 구간이나 운영 비율을 판정하지 않는다.
배포 환경에서는 내부 테스트 계정으로 서로 다른 직무·문항의 대표 케이스를 실행해 end-to-end
동작, 승인 후보 범위, fallback, 개인정보 비노출과 지표 수집 여부만 확인한다. 이미 완료한
20건 평가 smoke 결과는 `docs/evaluation/fewshot-canary-smoke-20260920.md`를 기준선으로 사용한다.

운영 트래픽이 없는 상태에서 5% rollout으로 selection 표본 20건을 억지로 만들기 위해 대량 요청을
생성하지 않는다. 추가 표본이 필요하면 운영 인스턴스의 rollout을 확대하지 않고 별도 내부 환경에서
검증한다.

| 항목 | 사전 검증 기준 | 관측값 | 판정 |
| --- | --- | --- | --- |
| 기동 readiness | 승인 데이터 5건 검증 통과 | 5건 | 통과 |
| 활성 profile | `prod,fewshot-canary` | 적용 | 통과 |
| 배포 이미지 | 배포 커밋 SHA와 일치 | 일치 | 통과 |
| 내부 분석 요청 | 대표 직무·문항 end-to-end 성공 | 2026-09-24 대표 분석 1건 정상 완료 | 통과 |
| 선택 mode | `EMBEDDING` 또는 의도된 `LOCAL_FALLBACK` | 확인 예정 | 보류 |
| 승인 후보 범위 | `dynamic few-shot selection completed` 로그의 `selectedIds`가 승인된 5개 ID의 부분집합 | 확인 예정 | 보류 |
| STATIC_FALLBACK | 내부 검증에서 0건 | 확인 예정 | 보류 |
| 개인정보 비노출 | 로그와 metric label에 원문 없음 | Cloud Loki의 `userId`, `clientIp` 마스킹 확인; 동적 선택 로그는 표본 대기 | 부분 통과 |
| Grafana 수집 | Few-shot 지표 조회 가능 | Prometheus remote write와 Loki 신규 로그 수집 확인; Few-shot 시계열은 동적 요청 대기 | 부분 통과 |
| Discord 경보 | Preview 정상, 테스트 알림 수신 | Grafana contact point 알림 수신 확인 | 통과 |

### 2026-09-24 내부 분석 및 관측 연결 확인

- 대표 분석 task `40b744bf-1ee5-41f8-a456-bd3286a01254`가 Worker에서 오류 없이 완료됐다.
- 해당 task의 SHA-256 rollout bucket은 `50`으로 5% 적용 조건인 `bucket < 5`에 포함되지 않았다.
  따라서 동적 선택 로그와 `fewshot_*` 시계열이 생성되지 않은 것은 기대 동작이다.
- Grafana Cloud Prometheus에서 `up{job="server_metric"}` 수집을 확인했다.
- Grafana Cloud Loki에서 `service_name="jobdri-api"`인 신규 요청 로그를 확인했다.
- Cloud Loki 신규 로그의 `userId`와 `clientIp`가 `[REDACTED]`로 치환되는 것을 확인했다.
- 동적 선택 표본을 만들기 위한 반복 요청이나 rollout 임시 확대는 수행하지 않고, 다음 자연스러운
  내부 분석이 5% cohort에 포함될 때 선택 mode와 승인 후보 범위를 검증한다.

## 서비스 오픈 후 관측

서비스 오픈 후 실제 트래픽이 발생하면 최소 1일과 하나의 일간 피크 구간을 포함하고, 최근
10분 selection 표본이 20건 이상인 시점에 아래 표를 작성한다. 표본 조건을 충족하기 전에는
비율만으로 확대 여부를 판정하지 않는다.

| 항목 | 통과 기준 | source metric·기준선 | 관측값 | 판정 |
| --- | --- | --- | --- | --- |
| Selection 표본 | 최근 10분 20건 이상 | `fewshot_selection_count_total`; 경보 공통 최소 표본 | 오픈 후 관측 | 보류 |
| STATIC_FALLBACK | 0건 | `fewshot_selection_count_total{mode="STATIC_FALLBACK"}`; 내부 smoke 0건 | 오픈 후 관측 | 보류 |
| LOCAL_FALLBACK | 25% 이하 | `fewshot_selection_count_total`; 내부 smoke 15% | 오픈 후 관측 | 보류 |
| Cohere 실패율 | 10분 selection 대비 5% 이하, 표본 20건 이상 | `fewshot_cohere_failure_count_total` / `fewshot_selection_count_total`; 내부 smoke 실패율 미계측 | 오픈 후 관측 | 보류 |
| Selection P95 | 10분 P95 2초 이하, 표본 20건 이상 | `fewshot_selection_duration_seconds_bucket`; selection 전용 baseline 미수립 | 오픈 후 관측 | 보류 |
| 분석 전체 P95 | 관측 전용 | Few-shot 전용 분리 불가; 평가 P95는 STATIC 8,344ms, DYNAMIC 7,471ms | 오픈 후 관측 | 확대 조건 제외 |
| Cohere 호출량 | 관측 전용 | `fewshot_cohere_logical_calls_total`; 내부 smoke 20건에서 21회 | 오픈 후 관측 | 확대 조건 제외 |
| OpenAI 입력 토큰 | 관측 전용 | Few-shot 전용 운영 지표 없음; 튜닝 평가 20건에서 265,266 | 오픈 후 관측 | 확대 조건 제외 |
| 승인 후보 범위 | 승인된 5개 ID만 선택 | privacy-safe `selectedIds` 로그; 내부 smoke 68개 선택 모두 승인 범위 | 오픈 후 관측 | 보류 |
| 품질 회귀 | unsupported fact·false positive 증가 없음 | 내부 STATIC/DYNAMIC 평가에서 각각 0% | 오픈 후 관측 | 보류 |

Cohere 실패율과 selection P95는 `docs/fewshot-alerting.md`의 10분 구간·최소 20건 정의를
그대로 사용한다.

```promql
(
  sum(increase(fewshot_cohere_failure_count_total[10m]))
  /
  clamp_min(sum(increase(fewshot_selection_count_total[10m])), 1)
) > 0.05
and
sum(increase(fewshot_selection_count_total[10m])) >= 20
```

```promql
histogram_quantile(
  0.95,
  sum(rate(fewshot_selection_duration_seconds_bucket[10m])) by (le)
) > 2
and
sum(increase(fewshot_selection_count_total[10m])) >= 20
```

분석 전체 P95의 평가 baseline은 `docs/evaluation/fewshot-tuning-20260916.md`, Cohere 호출량과
OpenAI 입력 토큰 baseline은 같은 문서 및 `docs/evaluation/fewshot-canary-smoke-20260920.md`에서
가져왔다. 세 항목은 운영에서 Few-shot 전용 baseline을 분리할 수 있을 때까지 관측만 하고
25% 확대의 필수 통과 조건으로 사용하지 않는다.

### 승인 후보 로그 검증

운영 로그에서 `dynamic few-shot selection completed`만 조회해 `selectedIds`가 `FS-02`, `FS-03`,
`FS-05`, `FS-08`, `FS-09`의 부분집합인지 확인한다. 로그에는 selection cache miss와 hit 모두
`cacheHit`, `selectedIds`, `sources`, `scores`, `datasetVersion`이 기록된다. ID는 비식별 식별자만
허용하며 답변, JD, 검색 텍스트와 embedding은 기록하지 않는다.

```bash
docker logs jobdri-api 2>&1 | grep 'dynamic few-shot selection completed'
```

최소 한 번은 동일 입력을 반복 실행해 `cacheHit=true` 로그에서도 선택 ID가 확인되는지 검증한다.

## 확대 또는 복귀 결정

- 현재 결정: 정식 서비스 오픈 전까지 5% 유지
- 오픈 전: 내부 end-to-end 검증만 완료하고 트래픽 비율 확대를 판단하지 않음
- 25% 확대: `판정`이 `보류`인 필수 항목을 모두 통과한 뒤 PM 또는 품질 담당자와
  백엔드·운영 담당자가 승인할 때만 진행한다. `확대 조건 제외` 항목은 기록하되 baseline 수립
  전에는 확대를 차단하는 조건으로 사용하지 않는다.
- 즉시 복귀: `STATIC_FALLBACK`, 승인되지 않은 후보 선택, 개인정보 노출, 반복적인 분석 오류나
  품질 악화가 확인되면 `SPRING_PROFILES_ACTIVE=prod`로 되돌린다.

상세 PromQL과 대응 절차는 `docs/fewshot-operations.md`, 경보 설정은
`docs/fewshot-alerting.md`, 전체 점검 순서는 `docs/fewshot-canary-checklist.md`를 따른다.
