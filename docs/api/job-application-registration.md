# 지원 카드 수동 등록

`POST /api/job-applications` — 로그인한 사용자의 지원 카드를 생성한다.

필수 입력은 `companyName`, `jobTitle`이다. `postingName`이 없거나 공백이면 직무명을 사용한다.
`initialStage`는 생략하거나 `PLANNED`만 전달할 수 있다. 등록 후 단계 변경은 기존 position API를 사용한다.

```json
{
  "companyName": "예시 기업",
  "jobTitle": "백엔드 개발자",
  "postingUrl": "https://example.com/jobs/1",
  "requiredSkills": ["Java", "Spring"],
  "deadlineAt": "2026-10-31T23:59:59",
  "gpa": 3.8,
  "maxGpa": 4.5,
  "metrics": [{"type": "LANGUAGE", "name": "TOEIC", "value": "920"}],
  "essays": [{"question": "지원 동기를 작성해주세요.", "answer": "작성 중인 답변"}]
}
```

- `postingUrl`: 선택 항목. HTTP 또는 HTTPS URL만 허용하며 최대 2,048자다. 등록 응답, 상세 조회·저장 응답, 보드 카드 응답에서 제공한다.
- `essays`: 선택 항목. 최대 20개, 질문 1,000자, 답변 10,000자. null 요소는 거부하고 질문이 비어 있는 행은 제외한다. 나머지 문항은 요청 순서대로 저장한다.
- `requiredSkills`: 앞뒤 공백을 제거하고 중복을 제외하며 첫 입력 순서를 유지한다.
- 학점과 정량 정보는 마스터 이력서에서 불러온 값을 요청에 복사해 제출한다. 경험·프로젝트는 복사 대상이 아니다.
- 공고와 정량 정보·자소서는 하나의 트랜잭션으로 저장한다. 등록 응답은 기존 카드 정보 형식을 유지하며, 자소서와 정량 정보 전체는 `GET /api/job-applications/{id}`로 조회한다.
- `PUT /api/job-applications/{id}`의 전체 저장에도 `postingUrl`을 전달한다. 생략하거나 null로 전달하면 링크를 지운다.

이 API는 사용자 확인 후 최종 저장하는 경로다. AI 분석 초안 반환 API와 URL 본문 추출은 별도 후속 작업이다.

기존 DB에는 `V202610111200__add_job_application_posting_url.sql`의 컬럼 추가가 필요하다.
