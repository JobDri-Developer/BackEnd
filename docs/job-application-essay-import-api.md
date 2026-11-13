# 다른 공고의 자기소개서 가져오기: 조회 API

관련 이슈: #407. 이 단계는 공고 검색 및 질문·답변 미리보기만 제공한다.
두 API 모두 인증이 필요하며 현재 공고(targetId)와 원본 공고(sourceId)의 소유권을 검사한다.

## 원본 공고 목록

`GET /api/job-applications/{targetId}/essay-sources`

| 쿼리 | 기본값 | 설명 |
| --- | --- | --- |
| query | 빈 문자열 | 회사명·공고명·직무명 부분 검색, 대소문자·앞뒤 공백 무시 |
| page | 0 | 0 기반 페이지. 음수는 0으로 보정 |
| size | 10 | 기존 PaginationPolicy 한도에 맞춰 보정 |
| includeArchived | false | true이면 보관된 본인 공고도 포함 |

문항이 있는 공고만 반환하며 현재 공고는 제외한다. 최근 등록순, 같은 등록 시각이면 ID 내림차순이다.
검색의 `%`, `_`, `!`는 와일드카드가 아닌 문자 자체로 처리한다.
보관 공고 노출 정책은 미확정이므로 호출자가 includeArchived로 범위를 명시할 수 있다.

응답은 공통 ApiResponse의 result에 Page를 반환한다. content의 항목은 다음 필드로 구성된다.

```json
{
  "jobApplicationId": 12,
  "companyName": "잡드리",
  "postingName": "백엔드 채용",
  "jobTitle": "백엔드 개발자",
  "essayQuestionCount": 2
}
```

일치하는 공고가 없으면 빈 content와 totalElements=0을 반환한다. 문항 개수가 여러 개여도 공고는 한 번만 집계한다.
목록에는 답변 본문을 포함하지 않는다.

## 원본 질문·답변 미리보기

`GET /api/job-applications/{targetId}/essay-sources/{sourceId}`

result는 displayOrder 오름차순(동률이면 essayId 오름차순)의 문항 배열이다.

```json
[
  {
    "essayId": 31,
    "question": "지원 동기",
    "answer": "답변 내용",
    "displayOrder": 0
  }
]
```

질문·답변은 줄바꿈과 null을 포함해 저장된 내용을 그대로 반환한다. 문항이 없으면 빈 배열이다.
sourceId=targetId이면 400, 다른 사용자 공고이면 403, 존재하지 않는 공고이면 404를 반환한다.
미리보기는 보관된 본인 원본 공고에도 접근 가능하다.

## 저장 경계와 남은 정책

- 두 API는 데이터를 변경하거나 문항을 복사·저장하지 않는다.
- essayId는 원본 문항 식별자다. 가져온 뒤 대상 공고의 문항 ID로 사용하지 않는다.
- 화면에서 선택한 질문·답변을 편집 폼에 채울 수 있다.
- 즉시 복사 저장/폼 반영 후 저장, 추가/교체, 중복 처리, 최종 보관 공고 노출 정책은 후속 결정 항목이다.
- 기존 상세 전체 저장을 연결할 경우 expectedRevision과 기존 전체 문항 목록을 포함하는 계약 및 문항 수·길이 제한을 따라야 한다.
- 조회 API 추가만으로 #407 전체 완료로 처리하지 않는다.
