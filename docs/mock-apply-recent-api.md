# 최근 모의지원 조회

홈의 최근 임시저장 모의지원은 `GET /api/mock-applies/me/recent?draftsOnly=true`로 조회한다. 로그인 사용자의 완료 전 상태(`APPLICATION_CREATED`, `QUESTION_SELECT`, `ANSWER_WRITE`)를 수정일과 ID 내림차순으로 정렬한다. `draftsOnly`를 생략하거나 `false`로 지정하면 완료 건도 포함한다.

응답의 `result`는 카드 배열이며, 대상이 없으면 빈 배열을 반환한다. 카드에 모의지원·공고 ID, 표시 이름, 회사명, 직무명, 소분류, 프로필 색상, 상태, 이어서 작성할 경로(`resumePath`)가 포함된다. 화면에 표시할 카드 개수는 별도 정책에서 정한다. API의 기존 `limit` 기본값은 5, 최대값은 20이다.
