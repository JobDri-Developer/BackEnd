# 지원 공고 등록 전 추출 미리보기 (#396)

인증된 사용자가 `POST /api/job-postings/extract/preview`에 기존 추출 요청 형식을 전달한다.

```json
{"rawText": "채용 공고 원문 텍스트"}
```

이미지 분석은 기존 업로드로 받은 `imageObjectKey` 또는 `imageObjectKeys`를 전달한다.
기존 추출의 입력 검증과 이미지 소유권 검사를 공유한다.
허용 형식은 PNG·JPEG(JPG)·WebP이며 기본 제한은 이미지당 **10 MB(10,000,000바이트)**다.
`POST /api/job-postings/images/presign-upload`로 URL을 받아 같은 Content-Type으로 S3에 PUT한다.
업로드 URL 발급 시 지원하지 않는 Content-Type을 거절한다.
미리보기 호출 시 키 소유권을 확인하고, S3 메타데이터의 형식·크기를 검증한다.
정확히 10,000,000바이트는 허용하고, 초과 이미지는 S3 PUT이 성공했어도 분석 전에 400으로 거절한다.
다른 사용자 키는 S3 조회 전에 403으로 거절한다.
이 요청은 JD·지원 카드·비동기 작업을 생성하지 않는다.

## 응답

성공 응답의 `result` 예시:

```json
{
  "draft": {
    "companyName": "예시 기업",
    "postingName": "백엔드 개발자 채용",
    "jobTitle": "백엔드 개발자",
    "task": "API 개발",
    "requirement": "Java 개발 경험",
    "preferred": "SQL 경험",
    "requiredSkills": ["Java", "SQL"],
    "deadlineAt": "2026-11-30T18:00:00"
  },
  "missingRequiredFields": []
}
```

- `draft`는 [수동 등록 요청](job-application-registration.md)의 필드명을 사용한다. 화면에서 확인·수정한 draft를 `POST /api/job-applications`에 별도로 제출한다.
- 미확인 문자열은 빈 문자열이다. 회사명·직무명이 비어 있으면 `missingRequiredFields`에 필드명을 포함한다. 목록이 비어 있어도 최종 등록의 길이 등 전체 검증을 통과했다는 의미는 아니다.
- 기술 태그는 앞뒤 공백을 제거하고 빈 값·50자 초과 값·중복을 제외해 처음 등장한 순서대로 최대 20개 반환한다.
- 마감일은 시간대 표시가 없는 ISO local date-time으로 파싱한다. 알 수 없거나 파싱할 수 없으면 null로 둔다. 임의 날짜·시간대를 정하지 않는다.
- URL 입력에서는 `draft.postingUrl`에 입력 링크가 포함된다. 정량 정보와 자기소개서 문항은 등록 폼에서 별도로 입력한다.

## Greenhouse 공개 상세 URL (#414)

```json
{"postingUrl":"https://job-boards.greenhouse.io/example/jobs/123"}
```

URL 요청은 텍스트·이미지와 함께 보낼 수 없다. HTTPS `job-boards.greenhouse.io/{board}/jobs/{id}` 상세 링크만 받으며 URL은 최대 2,048자다. 입력 링크의 쿼리와 프래그먼트는 초안에 보존하고, 수집은 보드와 ID로 만든 고정 호스트의 [공식 단건 API](https://docs.greenhouse.io/job-board#retrieve-a-job)로 요청한다. 외부 리다이렉트를 따르지 않는다.

응답에서 제목과 본문을 읽고 HTML을 평문으로 변환해 기존 텍스트 분석에 전달한다. 수집 요청 제한은 5초와 응답 512 KiB이며, 분석 입력은 기존 10,000자 제한을 적용한다. 사용자가 초안을 확인한 뒤 기존 등록 API로 제출하면 `postingUrl`도 저장된다.

| HTTP | 수집 오류 코드 |
| --- | --- |
| 400 | `POSTING_URL_INVALID`, `POSTING_URL_UNSUPPORTED` |
| 404 | `POSTING_SOURCE_NOT_FOUND` |
| 422 | `POSTING_CONTENT_EMPTY`, `POSTING_CONTENT_TOO_LARGE` |
| 502 | `POSTING_SOURCE_BLOCKED`, `POSTING_SOURCE_UNAVAILABLE` |
| 504 | `POSTING_SOURCE_TIMEOUT` |

수집 오류가 발생하면 분석을 호출하지 않는다.

## 상태 처리

| 상태 | 화면 처리 |
| --- | --- |
| 요청 진행 중 | 요청이 끝날 때까지 로딩 표시 |
| 200 | 초안을 채우고 누락 필수값 확인. 카드 저장은 사용자가 별도 요청 |
| 400 / 401 / 403 | 입력 또는 인증·권한 오류 안내 |
| 5xx | 실패 안내와 재시도 제공. AI 호출 예외를 빈 성공 초안으로 반환하지 않음 |

동기 API라 taskId·진행률·서버 취소는 제공하지 않는다. 화면에서 대기를 끝내도 서버가 이미 시작한 분석은 완료될 수 있다.
기존 `/extract`의 응답과 fallback 동작은 유지한다. `/ingest`의 상태·SSE·취소 API는 저장을 포함하는 기존 작업에만 해당하므로 미리보기에 사용하지 않는다.
