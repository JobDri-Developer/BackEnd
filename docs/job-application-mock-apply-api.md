# 지원관리 카드 모의지원 전환 API (#306)

## 요청

`POST /api/job-applications/{jobApplicationId}/mock-apply`

로그인 사용자가 소유한 지원 카드에서 명시적으로 모의 전형을 시작한다. 요청 본문은 없다.

최초 요청에서는 카드 스냅샷으로 별도의 `JobPosting`을 생성하고, `MockApplyService`를 통해 `ACTUAL` 타입 `MockApply`를 만든 뒤 카드의 `mockApplyId`에 연결한다. 세 변경은 한 트랜잭션에서 처리된다.

## 준비 상태

전환 시 카드를 바꾸지 않고 JD에만 아래 보정을 적용한다.

- `task`, `requirement`, `preferred`가 비어 있으면 직무명으로 기본 문장을 채운다.
  - 주요 업무: `{jobTitle} 업무 수행`
  - 자격 요건: `{jobTitle} 직무 수행에 필요한 역량`
  - 우대 사항: `{jobTitle} 관련 실무 경험`
- `detailClassificationId`가 없으면 카드 내용으로 소분류를 자동 분류한다. 분류는 Clipper ingest와 같은 후보 검색과 LLM 분류를 쓰고, 신뢰도 기준도 `job-posting.ingest.classification-confidence-threshold`(기본 0.65)를 공유한다. 분류는 행 잠금을 잡기 전에 끝낸다.

후보가 없거나 신뢰도가 기준보다 낮으면 어떤 엔티티도 생성하지 않고 HTTP 422를 반환한다. FE는 `classificationCandidates` 중 하나를 사용자에게 고르게 한 뒤 상세 저장(`detailClassificationId`)으로 카드에 반영하고 다시 호출한다.

```json
{
  "isSuccess": false,
  "code": "JOB_APPLICATION_NOT_READY",
  "message": "직무 소분류를 자동으로 정하지 못했습니다. 후보 중 하나를 선택해 저장한 뒤 다시 시도해주세요.",
  "result": null,
  "error": {
    "missingFields": ["detailClassificationId"],
    "classificationCandidates": [
      {
        "detailClassificationId": 12,
        "detailClassificationName": "백엔드 개발",
        "middleClassificationName": "개발",
        "bigClassificationName": "IT",
        "score": 0.41
      }
    ]
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
