# 동적 Few-shot 내부 Smoke 평가

이 절차는 운영 트래픽을 받지 않는 로컬·내부 환경에서 `analysis-eval,fewshot-canary` profile로
기존 평가 Runner를 실행합니다. 운영 profile과 API 서버는 실행하지 않습니다.

## 실행

holdout CSV는 승인 후보 5건의 원본이나 단순 비식별 변형을 포함하지 않는 20건 이상을 사용합니다.

```bash
export OPENAI_API_KEY=...
export COHERE_API_KEY=...
export CONFIRM_FEWSHOT_CANARY_COST=true

./scripts/run-fewshot-canary-smoke.sh \
  /absolute/path/holdout.csv \
  /absolute/path/fewshot-canary-smoke.csv
```

스크립트는 입력 파일, API 키, 명시적 비용 확인을 검사하고 기존 출력 파일을 덮어쓰지 않습니다.
외부 OpenAI·Cohere 비용이 실제 발생합니다. 키는 명령 인자나 저장소 파일에 넣지 않습니다.

## 선택 결과 검증

실행 로그의 `Few-shot evaluation metadata output`에 표시된 JSONL 경로를 사용합니다.

```bash
./scripts/validate-fewshot-canary-sidecar.sh \
  /absolute/path/fewshot-canary-smoke.csv.fewshot.RUN_ID.jsonl
```

검증 조건:

- 모든 행이 `SUCCESS`, `RECORDED`
- selection mode가 `EMBEDDING` 또는 `LOCAL_FALLBACK`
- 모든 선택 source가 `REVIEWED_PRODUCTION`
- 선택 ID가 FS-02, FS-03, FS-05, FS-08, FS-09 범위

검증 실패 시 운영 canary로 진행하지 않습니다. 분석 CSV의 실패 단계와 sidecar를 확인하되 원문
답변을 로그나 이슈에 복사하지 않습니다.
