# Java·Python 공고 추출 응답 계약 (#396 일부)

`JobPostingExtractResponse`의 필드명은 두 저장소에서 동일한 camelCase를 사용한다.
이번 변경은 기존 추출 모델에 requiredSkills·deadlineAt을 맞추는 범위다. 분석 후 폼 확인·등록 기능 전체를 완료한 것은 아니다.

| 필드 | Java / Python 타입 | 미입력 값 |
| --- | --- | --- |
| requiredSkills | List<String> / list[str] | [] |
| deadlineAt | String / str | 빈 문자열 |

새 필드가 없는 구버전 응답과 두 필드에 명시적 null이 들어온 응답 모두 []·빈 문자열로 정규화한다.
기존 postingName·companyName·jobTitle·task·requirements·preferredQualifications·rawText·confidence 계약은 유지한다.

## 추출 규칙

- requiredSkills는 원문에 명시된 기술명만 중복 없이 추출한다. 없으면 빈 배열이다.
- deadlineAt은 원문에서 확인 가능한 마감 시각을 yyyy-MM-dd'T'HH:mm:ss 형식으로 반환한다. 확인할 수 없으면 빈 문자열이다.
- 시간대 오프셋/Z를 붙이지 않는 기존 Java local date-time 계약을 따른다. 확인하지 못한 날짜·시각을 임의 생성하지 않는다.
- DTO에서는 날짜를 String으로 유지한다. 모델에 필드가 추가됐다고 날짜 형식 검증이나 LLM 추출 정확도를 보장하지 않는다.
- Python의 동기·비동기 추출은 공통 프롬프트·파서를 사용한다. 분류 요청·finalize·저장 결과의 nested extracted에도 필드를 유지한다.

## 검증과 배포 순서

두 저장소에 같은 job-posting-worker-finalize.json 샘플을 둬 독립적으로 계약을 검증한다.
BackEnd: src/test/resources/contracts/job-posting-worker-finalize.json
analysis-server: tests/fixtures/job-posting-worker-finalize.json

Python에서는 파싱·동기/비동기 mock 추출·저장 결과 JSON 재직렬화를 확인하고 Java에서는 같은 샘플의 역직렬화 및 저장 envelope 재직렬화를 확인한다.
구버전 Python은 새 필드를 버릴 수 있으므로 필드 활용 전 worker 모델·프롬프트 변경을 배포해야 한다. Java에는 이미 필드가 있으며 이번에는 null/생략 기본값을 명확히 한다.
라이브 OpenAI·RabbitMQ·DB·컨테이너 연동이나 실공고 정확도 평가는 별도다.
