# 지원관리 카드 모의지원 전환 API (#306)

## 요청

`POST /api/job-applications/{jobApplicationId}/mock-apply`

로그인 사용자가 소유한 지원 카드에서 명시적으로 모의 전형을 시작한다. 요청 본문은 없다.

최초 요청에서는 카드 스냅샷으로 별도의 `JobPosting`을 생성하고, `MockApplyService`를 통해 `ACTUAL` 타입 `MockApply`를 만든 뒤 카드의 `mockApplyId`에 연결한다. 세 변경은 한 트랜잭션에서 처리된다.

## 준비 상태

아래 필드가 모두 있어야 전환할 수 있다.

- `companyName`
- `postingName`
- `jobTitle`
- `detailClassificationId`
- `task`
- `requirement`
- `preferred`

누락된 필드가 있으면 어떤 엔티티도 생성하지 않고 HTTP 422를 반환한다.

```json
{
  "isSuccess": false,
  "code": "JOB_APPLICATION_NOT_READY",
  "message": "모의지원 전환에 필요한 정보가 부족합니다.",
  "result": null,
  "error": {
    "missingFields": ["detailClassificationId", "task"]
  }
}
```

## 성공과 재호출

```json
{
  "isSuccess": true,
  "result": {
    "jobApplicationId": 10,
    "jobPostingId": 20,
    "mockApplyId": 30,
    "status": "APPLICATION_CREATED",
    "created": true
  }
}
```

- 최초 생성은 `created=true`다.
- 같은 카드를 다시 요청하면 새 엔티티를 만들지 않고 기존 ID와 현재 `MockApplyStatus`를 반환하며 `created=false`다.
- 동일 카드의 동시 요청도 사용자 및 카드 행 잠금으로 하나만 생성된다.
- 카드 단계와 `MockApplyStatus`는 서로 독립적이다.
- 전환 후 카드를 수정해도 이미 생성된 `JobPosting`, 문항, 답변 및 분석 입력은 변경되지 않는다.
- 새 분석 회차는 기존 모의지원 재도전 API를 사용한다.
