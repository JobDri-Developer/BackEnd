# 지원관리 칸반 API (#303)

선행 브랜치: `feat/#301-job-application-domain`. 모든 API는 로그인 사용자 소유 카드만 처리한다.

## 보드 조회

`GET /api/job-applications/board?query=&sort=MANUAL`

- `sort`: `MANUAL`(기본), `CREATED_DESC`. 알 수 없는 값은 400.
- `query`: 앞뒤 공백을 제거하고 회사명·공고명·직무명을 대소문자 구분 없이 부분 검색한다. `%`, `_`, `!`도 일반 문자로 검색한다.
- 응답은 공통 `ApiResponse.result` 안의 `sort`, `columns`이다.
- `columns`는 항상 `PLANNED`, `DOCUMENT`, `INTERVIEW`, `COMPLETED` 순서이며 각 열은 `stage`, `count`, `cards`를 가진다. 빈 열도 반환한다.
- 보관된 카드는 제외하고 `count`는 검색된 카드 수다.
- MANUAL은 저장된 `stageOrder`, ID 순서다. CREATED_DESC는 생성 시각, ID 내림차순이며 저장 순서를 변경하지 않는다.
- 카드 필드: `jobApplicationId`, `companyName`, `postingName`, `jobTitle`, `requiredSkills`, `essayQuestionCount`, `deadlineAt`, `currentLabel`, `currentAt`, `stage`, `stageOrder`, `createdAt`, `updatedAt`.
- 일시는 ISO LocalDateTime, Asia/Seoul 기준이며 D-day와 표시 색상은 클라이언트에서 계산한다.
- 자소서 개수는 지원 카드 소유 `job_application_essays`의 실제 개수다. 상세 편집 API는 #302에서 추가한다. MockApply 문항은 집계하지 않는다.

## 카드 이동

`PATCH /api/job-applications/{jobApplicationId}/position`

```json
{"targetStage":"INTERVIEW","targetIndex":0}
```

- targetIndex는 이동 카드를 제거한 뒤 도착 열에 삽입할 0 기반 위치다. 같은 열에서 맨 뒤는 원래 카드 수 - 1, 다른 열에서 맨 뒤는 도착 열의 카드 수다.
- 검색으로 감춰진 카드까지 포함한 **전체 열** 기준 위치다. 필터된 목록의 인덱스를 그대로 전송하면 안 된다.
- 클라이언트는 CREATED_DESC에서 드래그를 비활성화한다.
- 저장 후 전체 MANUAL 보드를 반환한다. 양쪽 열 순서와 갱신된 updatedAt을 함께 반영할 수 있다.
- 잘못된 단계·누락/음수/범위 초과 인덱스: 400. 존재하지 않는 카드: JOB_APPLICATION_4041. 다른 사용자 카드: 403. 보관된 카드: JOB_APPLICATION_4091 (409).
- 이동은 사용자 잠금 → 출발/도착 열의 활성 카드 잠금 → 양쪽 열 재정렬 → flush 순서의 단일 트랜잭션이다. 사용자 잠금은 빈 열과 #301의 동시 등록을 보호한다.
- 후속 보관·복원·삭제 구현에서도 순서 변경 전에 동일 사용자 잠금을 획득해야 한다.
- JOB_APPLICATION_MOVE 감사 이벤트를 기록한다.

## 조회와 DB 적용

기술 태그는 fetch join, 문항 개수는 사용자별 GROUP BY 한 번으로 조회한다. 사용자 인증 조회를 포함해 카드가 늘어도 조회 쿼리는 최대 3개다.

운영에는 #301 마이그레이션 적용 후 `ops/db/migrations/20260914_job_application_essays.sql`을 적용한다. 지원 카드 삭제 시 자소서는 함께 삭제되며 공고와 모의지원은 영향을 받지 않는다.
