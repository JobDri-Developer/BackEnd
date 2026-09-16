# 지원관리 보관함 API

기본 경로는 `/api/job-applications`이며 모든 API는 로그인 사용자가 소유한 카드만 처리한다.

## 카드 보관과 복원

- `POST /{jobApplicationId}/archive`: 카드에 보관 시각을 기록하고 활성 보드에서 제외한다. 빠진 카드가 있던 단계의 수동 순서는 0부터 연속으로 다시 정리한다.
- `POST /{jobApplicationId}/restore`: 카드가 보관 전에 속했던 단계를 유지하며 해당 단계의 마지막 순서로 복원한다.

이미 보관된 카드를 다시 보관하거나 활성 카드를 복원하면 `409 JOB_APPLICATION_4091`을 반환한다.

## 보관함 조회

`GET /archive?page=0&size=10`은 현재 사용자의 보관 카드만 `archivedAt DESC, id DESC` 순서로 페이지 조회한다. `page`는 최소 0으로, `size`는 1부터 공통 최대 페이지 크기까지 보정한다.

## 영구 삭제

`DELETE /{jobApplicationId}`는 지원 카드와 카드가 직접 소유한 기술 태그·체크리스트·정량 스펙·실제 제출용 자기소개서만 삭제한다. `sourceJobPostingId`가 가리키는 저장 공고와 `mockApplyId`가 가리키는 모의지원 및 분석 결과는 삭제하지 않는다.

반대로 출처 공고나 연결된 모의지원이 먼저 삭제되면 연결 ID만 `null`이 되며 카드의 독립 스냅샷은 유지된다.
