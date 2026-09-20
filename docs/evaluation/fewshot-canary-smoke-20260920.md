# 동적 Few-shot 내부 Canary Smoke (2026-09-20)

## 결론

20건 모두 성공했고 선택 메타데이터도 모두 기록됐습니다. 선택된 68개 후보는 전부
`REVIEWED_PRODUCTION`이며 승인 ID 범위를 벗어나지 않았습니다. 내부 선택 smoke는 통과했습니다.

## 결과

| 항목 | 결과 |
| --- | ---: |
| 전체 / 성공 | 20 / 20 |
| 메타데이터 RECORDED | 20 |
| EMBEDDING | 17 (85%) |
| LOCAL_FALLBACK | 3 (15%) |
| STATIC_FALLBACK | 0 |
| 총 선택 후보 | 68 |
| 평균 선택 후보 | 3.4 |
| Cohere 논리 호출 | 21 |
| embedding similarity | 0.401~0.524, 평균 0.447 |

LOCAL_FALLBACK은 EV-03, EV-06, EV-09에서 발생했습니다. 세 건 모두 `minSimilarity=0.40`을
통과한 후보가 `minimumSelectedCount=2`보다 적어 의도된 로컬 선택으로 전환된 경우입니다.

## 모니터링 결정

기존 LOCAL+STATIC fallback 10% 경보는 정상 기준선 15%보다 낮아 사용하지 않습니다.

- LOCAL_FALLBACK: 10분간 최소 20건, 25% 초과 시 경보
- STATIC_FALLBACK: 10분간 최소 20건, 1건 이상이면 경보
- Cohere 실패 5%, selection P95 2초 기준은 유지

이 결과는 내부 선택 경로 검증이며 운영 5% canary 관측을 대체하지 않습니다.
