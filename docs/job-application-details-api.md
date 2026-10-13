# 지원관리 상세 API

기본 경로는 `/api/job-applications`이며 모든 요청은 로그인 사용자의 카드만 조회하거나 수정할 수 있다. 카드의 공고 정보와 상세 항목은 원본 `JobPosting` 및 `MockApply`와 자동 동기화되지 않는 독립 스냅샷이다.

## 상세 조회

`GET /api/job-applications/{jobApplicationId}`는 공고 스냅샷, 현재 일정/라벨, 메모, 학점과 체크리스트·정량 스펙·실제 제출용 자기소개서를 반환한다. 각 목록은 저장된 `displayOrder` 오름차순이다.

## 상세 전체 저장

`PUT /api/job-applications/{jobApplicationId}`는 아래 데이터를 한 트랜잭션에서 저장한다.

- 공고 스냅샷: 회사명, 공고명, 직무명, 회사 규모, 직무 분류, JD 상세, 기술 태그, 마감 일시
- 진행 정보: 자유 형식 라벨과 예정 일시, 지원 및 면접 메모
- 정량 정보: `gpa`, `maxGpa`, `metrics`
- 세부 목록: `checklistItems`, `metrics`, `essays`

목록 필드가 누락되거나 `null`이면 빈 목록으로 간주하며, 전달된 목록은 기존 목록을 완전히 교체한다.

### 동시 수정 충돌

상세 조회와 저장 응답의 `revision`을 다음 요청의 `expectedRevision`으로 전달한다. 서버의 revision과 다르면 `409 JOB_APPLICATION_4091`을 반환한다. revision은 상세 저장 때만 오르며, 드래그·보관·복원처럼 순서만 바뀌는 변경은 revision을 올리지 않는다.

`lastKnownUpdatedAt`은 구 클라이언트 호환용으로만 남아 있다. `expectedRevision`이 없을 때만 `updatedAt`과 비교하며, 같은 열의 다른 카드를 옮겨도 `updatedAt`이 바뀌어 불필요한 409가 날 수 있다. 둘 다 없으면 400이다.

### 단계 변경

`stage`를 넣으면 같은 트랜잭션에서 단계를 바꾼다. 현재 단계와 다르면 카드를 도착 열의 마지막으로 옮기고 출발 열의 순서를 당긴다. 생략하거나 현재 단계와 같으면 단계와 순서를 유지한다. 보관된 카드의 단계를 바꾸면 409다. 따라서 상세 모달에서는 `PATCH /position`을 따로 부르지 않는다.

### 정량 스펙

정량 스펙의 `type`은 `CERTIFICATE`, `LANGUAGE`, `AWARD`, `CUSTOM` 중 하나다. 등록·상세 저장·마스터 이력서 저장이 같은 규칙으로 빈 행을 정리한다.

- `name`이 비어 있으면 행을 제외한다.
- `name`이 있는데 `type`이 없으면 400이다.
- `value`가 비어 있으면 `CERTIFICATE`는 `보유`, `AWARD`는 `취득일 미입력`으로 저장하고, `LANGUAGE`와 `CUSTOM`은 행을 제외한다.
- 제외 후 남은 행에 0부터 `displayOrder`를 다시 매긴다.

학점과 만점은 둘 다 입력하거나 둘 다 비워야 하며 `0 <= gpa <= maxGpa`여야 한다.

기본 제한은 기술 태그 20개(각 50자), 체크리스트 100개(각 200자), 자기소개서 20개(질문 1,000자·답변 10,000자), 메모 10,000자, 진행 라벨 100자다. 모든 일시는 ISO `LocalDateTime` 형식이며 Asia/Seoul 기준으로 해석한다.
