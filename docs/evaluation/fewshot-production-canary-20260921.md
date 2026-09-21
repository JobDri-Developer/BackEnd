# 동적 Few-shot 운영 5% Canary 관측 기록

## 배포 기준선

| 항목 | 값 |
| --- | --- |
| 상태 | 정식 서비스 오픈 전 사전 검증 중 |
| 관측 시작 | 2026-09-21 17:20 KST |
| Backend 이미지 | `ghcr.io/jobdri-developer/backend:db89b8d1cf0f776695b3be5eb358fadc86ab887b` |
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
- 배포 워크플로가 서버에서 `github.sha` 태그를 사용하도록 변경한 뒤 위 Backend 이미지로
  재배포했다.
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
| 내부 분석 요청 | 대표 직무·문항 end-to-end 성공 | 확인 예정 | 보류 |
| 선택 mode | `EMBEDDING` 또는 의도된 `LOCAL_FALLBACK` | 확인 예정 | 보류 |
| 승인 후보 범위 | 승인된 5개 ID만 선택 | 확인 예정 | 보류 |
| STATIC_FALLBACK | 내부 검증에서 0건 | 확인 예정 | 보류 |
| 개인정보 비노출 | 로그와 metric label에 원문 없음 | 확인 예정 | 보류 |
| Grafana 수집 | Few-shot 지표 조회 가능 | 확인 예정 | 보류 |
| Discord 경보 | Preview 정상, 테스트 알림 수신 | 확인 예정 | 보류 |

## 서비스 오픈 후 관측

서비스 오픈 후 실제 트래픽이 발생하면 최소 1일과 하나의 일간 피크 구간을 포함하고, 최근
10분 selection 표본이 20건 이상인 시점에 아래 표를 작성한다. 표본 조건을 충족하기 전에는
비율만으로 확대 여부를 판정하지 않는다.

| 항목 | 통과 기준 | 관측값 | 판정 |
| --- | --- | --- | --- |
| Selection 표본 | 최근 10분 20건 이상 | 오픈 후 관측 | 보류 |
| STATIC_FALLBACK | 0건 | 오픈 후 관측 | 보류 |
| LOCAL_FALLBACK | 25% 이하 | 오픈 후 관측 | 보류 |
| Cohere 실패율 | 5% 이하 | 오픈 후 관측 | 보류 |
| Selection P95 | 2초 이하 | 오픈 후 관측 | 보류 |
| 분석 전체 P95 | 기존 기준 대비 20% 미만 증가 | 오픈 후 관측 | 보류 |
| Cohere 호출량 | 예상 범위 대비 20% 미만 증가 | 오픈 후 관측 | 보류 |
| OpenAI 입력 토큰 | 예상 범위 대비 20% 미만 증가 | 오픈 후 관측 | 보류 |
| 승인 후보 범위 | 승인된 5개 ID만 선택 | 오픈 후 관측 | 보류 |
| 품질 회귀 | unsupported fact·false positive 증가 없음 | 오픈 후 관측 | 보류 |

## 확대 또는 복귀 결정

- 현재 결정: 정식 서비스 오픈 전까지 5% 유지
- 오픈 전: 내부 end-to-end 검증만 완료하고 트래픽 비율 확대를 판단하지 않음
- 25% 확대: 모든 관측 기준을 충족한 뒤 PM 또는 품질 담당자와 백엔드·운영 담당자가 승인할 때만 진행
- 즉시 복귀: `STATIC_FALLBACK`, 승인되지 않은 후보 선택, 개인정보 노출, 반복적인 분석 오류나
  품질 악화가 확인되면 `SPRING_PROFILES_ACTIVE=prod`로 되돌린다.

상세 PromQL과 대응 절차는 `docs/fewshot-operations.md`, 경보 설정은
`docs/fewshot-alerting.md`, 전체 점검 순서는 `docs/fewshot-canary-checklist.md`를 따른다.
