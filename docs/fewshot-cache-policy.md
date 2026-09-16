# Few-shot 메모리 캐시 정책

## 기본값

| 캐시 | 최대 크기 | TTL |
| --- | ---: | ---: |
| selection | 1,000 | 30분 |
| query embedding | 1,000 | 30분 |
| document embedding | 5,000 | 30분 |

환경변수로 조정할 수 있습니다.

```text
ANALYSIS_FEW_SHOT_CACHE_TTL=30m
ANALYSIS_FEW_SHOT_SELECTION_CACHE_MAX_SIZE=1000
ANALYSIS_FEW_SHOT_QUERY_EMBEDDING_CACHE_MAX_SIZE=1000
ANALYSIS_FEW_SHOT_DOCUMENT_EMBEDDING_CACHE_MAX_SIZE=5000
```

모든 최대 크기는 1 이상으로 보정합니다. selection과 document embedding 캐시는 접근할 때마다
만료 항목을 먼저 제거하고, 상한을 넘으면 마지막 접근 시각이 오래된 항목부터 제거합니다.
query embedding 캐시는 기존 주기적 정리와 LRU 제거를 유지합니다.

selection과 document embedding 생성이 진행 중인 키는 제거 대상에서 제외합니다. 생성이 끝나 in-flight 상태가
해제된 직후 다시 상한을 적용하므로, 동시에 한 batch가 완료되는 짧은 구간에는 상한을 일시적으로
넘을 수 있지만 장시간 초과 상태로 남지 않습니다. selection/query/document의 in-flight future는
캐시 제거와 별도로 유지되므로 동일 키의 동시 요청이 외부 API를 중복 호출하지 않습니다.

## 관측

`fewshot_cache_events_total`을 `cache`, `outcome` 태그로 나눠 확인합니다.

```promql
sum(rate(fewshot_cache_events_total[10m])) by (cache, outcome)

sum(rate(fewshot_cache_events_total{outcome="hit"}[10m])) by (cache)
/
sum(rate(fewshot_cache_events_total{outcome=~"hit|miss"}[10m])) by (cache)

sum(increase(fewshot_cache_events_total{outcome="evicted"}[1h])) by (cache)
```

eviction이 지속 증가하면서 hit 비율이 낮으면 캐시 상한을 늘리기 전에 고유 질의 수, 데이터셋 변경
빈도와 실제 메모리 사용량을 함께 확인합니다.
