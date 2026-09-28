# 지원관리 Clipper ingest API (#305)

## 요청

`POST /api/job-applications/ingest`

로그인 사용자의 Clipper 입력을 기존 공고 추출·직무 분류 컴포넌트로 처리한 뒤, `JobApplication` 스냅샷만 생성한다.

```json
{
  "idempotencyKey": "clipper-request-20260924-001",
  "rawText": "채용 공고 원문",
  "imageObjectKey": null,
  "imageObjectKeys": [
    "job-postings/tmp/42/first.png",
    "job-postings/tmp/42/second.png"
  ]
}
```

- `idempotencyKey`: 필수, 사용자별 최대 100자. 동일 사용자가 같은 키를 재전송하면 기존 카드를 반환한다.
- `rawText`, `imageObjectKey`, `imageObjectKeys` 중 하나 이상이 필요하다.
- 이미지는 기존 공고 이미지 업로드 API에서 발급받은 현재 사용자 소유 object key를 사용하며 최대 2개다.
- 카드에는 회사명·공고명·직무명·주요 업무·자격 요건·우대 사항·기술 태그·마감일과 분류 결과를 복사한다.
- 새 카드는 `PLANNED` 열의 마지막 순서로 저장된다.

## 응답

성공 및 멱등 재요청은 HTTP 200이며 `savedToDatabase=true`다. 최초 생성은 `idempotentReplay=false`, 기존 카드 반환은 `true`다.

```json
{
  "isSuccess": true,
  "result": {
    "savedToDatabase": true,
    "idempotentReplay": false,
    "message": "지원 카드 등록에 성공했습니다.",
    "extracted": {},
    "candidates": [],
    "classification": {},
    "generated": {},
    "jobApplication": {
      "jobApplicationId": 101,
      "sourceJobPostingId": null,
      "mockApplyId": null,
      "stage": "PLANNED",
      "stageOrder": 0
    }
  }
}
```

분류 confidence가 설정 임계값보다 낮으면 HTTP 200과 `savedToDatabase=false`를 반환하고 카드를 만들지 않는다. 추출 필수값 누락은 기존 ingest와 동일한 `INVALID_JOB_POSTING_FIELDS` 오류 계약을, 분류 후보 없음은 `CLASSIFICATION_NOT_FOUND`를 사용한다.

이 경로에서는 `JobPosting`, `MockApply`, 자소서 분석 결과를 생성하지 않는다. 성공한 생성과 멱등 재요청은 `JOB_APPLICATION_CLIPPER_INGEST` 감사 이벤트로 남는다.
