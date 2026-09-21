# 동적 Few-shot Canary 점검표

## 배포 전

- [ ] PM 승인 후보 5건과 datasetVersion `fewshot-pm-reviewed-20260914-v2`를 확인한다.
- [ ] `reviewed-production-resource`가 승인 리소스를 가리키는지 확인한다.
- [ ] `fixed`, `curated`, `reviewed-evaluation` 소스는 비활성화한다.
- [ ] `reviewed-production` 소스만 활성화한다.
- [ ] Cohere API 키·모델·timeout 설정을 확인한다.
- [ ] `JobDri / Dynamic Few-shot` 대시보드가 운영 Prometheus를 조회하는지 확인한다.
- [ ] fallback, Cohere 실패, selection P95 경보가 저장되어 있는지 확인한다.
- [ ] `jobdri-discord-alerts` contact point 테스트 메시지를 확인한다.
- [ ] 담당자가 feature flag 비활성 재배포를 실행할 수 있는지 확인한다.

초기 활성 환경변수:

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

같은 설정을 전용 profile로 적용하려면 운영 API에 다음을 지정합니다.

```text
SPRING_PROFILES_ACTIVE=prod,fewshot-canary
```

`fewshot-canary` profile은 `single-pass`, 승인 소스·검색값과 worker rollout 5%를 함께
적용합니다. Backend가 `taskId`의 SHA-256 버킷으로 적용 대상을 고정하므로 Nginx 트래픽
분리는 사용하지 않습니다. 환경변수 방식과 profile 방식 중 하나만 선택해 배포 기록에 남깁니다.

배포 순서는 optional `fewShot` context를 이해하는 `analysis-worker`를 먼저 배포한 다음
Backend에 `prod,fewshot-canary` profile을 적용합니다. 기존 worker 버전에는 이 profile을
활성화하지 않습니다.

Worker의 전체 분석 프롬프트 문자 예산은 기본 120,000자이며 필요 시 다음 환경변수로 낮출 수
있습니다. Backend와 worker는 Few-shot prompt block을 최대 20,000자로 동일하게 제한합니다.

```text
APP_WORKER_ANALYSIS_PROMPT_MAX_CHARS=120000
```

인스턴스 시작 시 전용 검증기가 다음을 확인합니다.

- dynamic selection과 `single-pass`가 활성화됐는지
- worker rollout percentage가 1~100 범위인지
- `REVIEWED_PRODUCTION` 외 소스가 꺼져 있는지
- 유효한 운영 승인 후보가 하나 이상인지
- 후보의 datasetVersion이 설정값과 같은지

하나라도 충족하지 않으면 canary 인스턴스는 시작을 중단합니다. 정상적으로
시작하면 `fewshot-canary readiness validated` 로그에 datasetVersion, rollout 비율, 후보 수, ID가
남습니다. 답변 원문과 embedding은 로그에 남지 않습니다.

## 내부 환경

- [ ] `docs/fewshot-canary-smoke.md`의 절차로 내부 smoke 평가를 실행한다.
- [ ] 운영 트래픽을 받지 않는 내부 인스턴스에서 먼저 활성화한다.
- [ ] 서로 다른 직무·문항의 테스트 분석을 최소 20건 실행한다.
- [ ] selection mode에 `EMBEDDING` 또는 의도된 `LOCAL_FALLBACK`이 기록되는지 확인한다.
- [ ] 선택 source가 `REVIEWED_PRODUCTION`인지 로그 또는 평가 메타데이터로 확인한다.
- [ ] 선택 ID가 FS-02, FS-03, FS-05, FS-08, FS-09 범위인지 확인한다.
- [ ] 원문 JD·답변·embedding이 로그나 metric label에 노출되지 않는지 확인한다.
- [ ] 네 Grafana 경보의 Preview가 오류 없이 평가되는지 확인한다.
- [ ] feature flag를 끈 뒤 기존 정적 Few-shot 경로로 복귀하는지 한 번 검증한다.

## 운영 5% Canary

- [ ] `worker-rollout-percentage=5`가 적용되고 Nginx 분배 없이 task cohort가 고정되는지 확인한다.
- [ ] 배포 시각, 인스턴스, datasetVersion, 검색 설정, 담당자를 기록한다.
- [ ] 최소 1일 및 하나의 일간 피크 구간을 관측한다.
- [ ] 최근 10분 selection 표본이 20건 이상인지 함께 확인한다.
- [ ] LOCAL_FALLBACK 비율이 25% 이하인지 확인한다.
- [ ] STATIC_FALLBACK이 0건인지 확인한다.
- [ ] Cohere 실패 비율이 5% 이하인지 확인한다.
- [ ] selection P95가 2초 이하인지 확인한다.
- [ ] 분석 전체 P95가 기존 기준보다 20% 이상 증가하지 않았는지 확인한다.
- [ ] Cohere 호출량과 OpenAI 입력 토큰이 예상 범위를 20% 이상 초과하지 않았는지 확인한다.
- [ ] 회귀 표본에서 unsupported fact 또는 false positive가 증가하지 않았는지 확인한다.

## 중단 및 복귀

다음 중 하나면 확대하지 않고 원인을 확인한다.

- Grafana 경보가 Firing 상태가 됐다.
- 승인되지 않은 ID 또는 예상하지 않은 source가 선택됐다.
- 원문 개인정보가 로그나 metric label에 노출됐다.
- 분석 오류 또는 품질 악화가 반복됐다.

복귀 절차:

1. `SPRING_PROFILES_ACTIVE=prod`로 되돌리거나 `ANALYSIS_FEW_SHOT_DYNAMIC_SELECTION_ENABLED=false`로 재배포한다.
2. 신규 분석이 기존 정적 Few-shot 경로로 처리되는지 확인한다.
3. Discord 경보가 Resolved로 전환되는지 확인한다.
4. 발생 시각, datasetVersion, 선택 ID, failure reason, P95를 기록한다.
5. 승인 리소스와 캐시는 삭제하지 않고 원인 수정 후 내부 환경에서 다시 검증한다.

## 확대 승인

- [ ] 5% 단계의 모든 점검 항목을 통과했다.
- [ ] PM 또는 품질 담당자가 회귀 표본을 확인했다.
- [ ] 백엔드·운영 담당자가 비용과 지연 증가를 확인했다.
- [ ] Discord 경보와 rollback 담당자가 지정됐다.
- [ ] 다음 단계(25%)의 시작 시각과 관측 종료 시각을 기록했다.

25% 이후에도 같은 점검표를 사용해 50%, 100% 순으로 확대합니다. 각 단계는 최소 하나의 일간
피크 구간을 포함하고, 중단 기준이 발생하면 즉시 이전 단계 또는 feature flag 비활성으로
복귀합니다.
