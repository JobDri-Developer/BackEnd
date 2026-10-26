# 지원 공고 즐겨찾기

#405의 즐겨찾기 범위만 구현한다. 상태·일정 필터는 포함하지 않는다.

## 설정·해제

`PATCH /api/job-applications/{jobApplicationId}/favorite`

```json
{"favorite": true}
```

false는 해제다. favorite는 필수이며 누락/null이면 400이다. 같은 값을 반복해도 토글되지 않는다.
인증이 필요하고 본인 공고에만 적용된다. 타 사용자 공고는 403, 없는 공고는 404다.
보관된 본인 공고에도 설정 가능하지만 보관된 공고는 즐겨찾기 여부와 무관하게 보드에서 제외된다.

성공 응답의 result:

```json
{"jobApplicationId": 12, "favorite": true}
```

단계·stageOrder·공고 상세·contentRevision은 바꾸지 않는다. 다른 카드 쓰기와 같은 사용자→카드 잠금 순서를 사용한다.
값이 바뀌면 JPA 감사 updatedAt은 갱신될 수 있으므로 상세 저장에는 expectedRevision을 권장한다.
구형 lastKnownUpdatedAt 클라이언트는 즐겨찾기 변경 후 다시 조회해야 할 수 있다.
상세 전체 저장 요청은 favorite를 받지 않으며 현재 즐겨찾기를 보존한다.

## 보드 필터 및 응답

`GET /api/job-applications/board?favoritesOnly=true`

기본값 false는 기존 전체 활성 보드다. true이면 본인의 즐겨찾기한 활성 공고만 반환한다.
query·sort와 함께 사용할 수 있고 각 컬럼의 count는 검색·즐겨찾기 조건을 적용한 결과다.
정렬과 stageOrder 저장값을 변경하지 않는다. 빈 컬럼도 count=0과 빈 cards로 유지한다.
보드 카드·상세·일반 카드 응답에 boolean favorite를 포함한다.

## 데이터베이스 적용

기본값은 false다. 기존 행도 false로 채워진다.
`V202610261200__add_job_application_favorite.sql` 및 schema.sql에 반영했다.
환경의 기존 마이그레이션/스키마 초기화 절차에 따라 적용해야 한다.
테스트 프로필 Hibernate create-drop만으로 운영 DB의 컬럼 적용 여부를 확인할 수는 없다.
