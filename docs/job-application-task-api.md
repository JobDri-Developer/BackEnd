# 공고별 할 일 API

기본 경로는 `/api/job-applications/{jobApplicationId}/tasks`다. 본인 공고에 한해 조회하고 변경할 수 있다. 다른 사용자의 공고에는 403을 반환하고, 공고 또는 해당 공고에 속한 할 일이 없으면 404를 반환한다. 보관 상태와 관계없이 기존 공고 상세와 같은 소유권 기준을 적용한다.

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| GET | 기본 경로 | 등록 ID 오름차순으로 목록 조회 |
| POST | 기본 경로 | 미완료 할 일 등록 |
| GET | `/{taskId}` | 상세 조회 |
| PUT | `/{taskId}` | 완료 상태를 유지하며 나머지 내용 교체 |
| PATCH | `/{taskId}/completion` | `{"completed": true}` 또는 `{"completed": false}`로 상태 설정 |
| DELETE | `/{taskId}` | 할 일과 하위 체크리스트 삭제 |

POST와 PUT 요청 예시

```json
{
  "title": "지원서 제출 준비",
  "dueDate": "2026-11-15",
  "memo": "지원 양식 확인",
  "checklistItems": [
    {"content": "공고 확인", "completed": true},
    {"content": "자기소개서 검토", "completed": false}
  ]
}
```

- 제목과 하위 항목 내용은 공백만 입력할 수 없으며 각각 최대 200자다. 메모는 최대 2000자다.
- 하위 체크리스트는 할 일당 최대 100개다. POST 또는 PUT 요청에서 초과하면 400을 반환한다.
- `dueDate`는 선택 날짜(YYYY-MM-DD)다. PUT에서 기한·메모를 생략하거나 null을 보내면 값을 비운다. 하위 목록을 생략하거나 빈 배열을 보내면 하위 항목을 모두 비운다.
- 응답은 공통 `ApiResponse.result`에 담긴다. 할 일에는 `taskId`, 입력 필드, `completed`, `completedChecklistCount`, `totalChecklistCount`가 포함된다. 하위 항목은 요청 순서로 반환된다.
- 할 일 완료 여부와 하위 항목 완료 여부는 독립적이다. 같은 완료 상태를 다시 설정해도 결과는 같다. 변경 요청은 같은 공고 행 잠금으로 직렬화하며, PUT은 마지막으로 반영된 요청의 내용을 저장한다.
- 신규 테이블은 `V202611101200__create_job_application_tasks.sql`에서 추가한다. 기존 공고 상세의 체크리스트는 별도로 유지한다. 기존 데이터 이전, 오늘·예정·완료 필터와 홈 통합 조회는 후속 범위다.

## 사용자 전체 미완료 할 일 조회

`GET /api/job-applications/tasks/incomplete`는 로그인 사용자의 모든 공고에서 미완료 할 일을 조회한다. 응답의 `result`는 배열이며 대상이 없으면 `[]`다. 각 항목에는 `taskId`, `jobApplicationId`, `postingName`, `companyName`, `title`, `dueDate`, `completedChecklistCount`, `totalChecklistCount`가 포함된다. 체크리스트가 비어 있으면 두 건수는 모두 0이다.

현재 조회는 공고의 보관 상태와 할 일 기한에 관계없이 반환한다. 응답 순서나 표시 개수를 보장하는 계약은 정책 확정 후 정한다. 완료 처리는 기존 공고별 API를 사용한다.
