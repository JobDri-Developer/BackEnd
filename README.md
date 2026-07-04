# BackEnd
Repository of JobDri BackEnd

## Docker

로컬 실행:

```bash
cp .env.example .env
docker compose up --build
```

포함 서비스:

- Spring Boot API: `http://localhost:8080`
- FastAPI worker health: `http://localhost:8000/health`
- RabbitMQ: `amqp://localhost:5672`
- RabbitMQ management: `http://localhost:15672`

배포 서버 실행:

```bash
cp .env.production.example .env
docker compose -f docker-compose.prod.yml up -d
```

`prod` 프로필은 `/actuator/health`를 노출합니다.

## Async Worker Architecture

현재 비동기 구조는 아래처럼 분리됩니다.

- API 서버: task row 생성, 상태 조회 API 제공, RabbitMQ publish, 최종 DB 저장
- RabbitMQ: `job posting ingest` 작업 버퍼링과 재전달
- FastAPI worker: MQ consume, OpenAI 기반 추출/분류/생성 수행, 내부 API callback

로컬에 필요한 추가 env:

```bash
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/
APP_WORKER_INTERNAL_API_KEY=change-me-internal-worker-key
APP_WORKER_JOB_POSTING_EXCHANGE=jobdri.worker.exchange
APP_WORKER_JOB_POSTING_QUEUE=jobdri.job-posting.ingest
APP_WORKER_JOB_POSTING_ROUTING_KEY=job-posting.ingest
APP_WORKER_JOB_POSTING_DLQ=jobdri.job-posting.ingest.dlq
WORKER_MAX_RETRY_COUNT=3
```

스모크 테스트:

1. `docker compose up --build`
2. 로그인 토큰을 준비합니다.
3. `POST /api/job-postings/ingest` 로 `rawText` 또는 `imageObjectKey`를 전송합니다.
4. 응답의 `taskId`로 `GET /api/job-postings/ingest/async/{taskId}` 를 조회합니다.
5. worker가 성공하면 `SUCCEEDED`, 실패하면 `FAILED` 와 에러 메시지가 반환됩니다.

worker 단독 실행:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r worker/requirements.txt
uvicorn worker.app.main:app --host 0.0.0.0 --port 8000
```

## Corpus Import Script

대용량 corpus 엑셀 적재는 관리자 API 대신 Python 스크립트로 직접 실행할 수 있습니다.

설치:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r scripts/requirements-corpus-import.txt
```

실행:

```bash
cp .env.example .env
python scripts/import_corpus.py /absolute/path/to/file.xlsx
```

- `.env`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 사용합니다.
- 기본 env 파일 경로를 바꾸려면 `--env-file /path/to/.env` 옵션을 사용할 수 있습니다.
- 엑셀 시트명은 `jd_embed_corpus`, `question_embed_corpus`를 사용합니다.
- `source_analysis_id`, `source_question_id` 기준으로 `INSERT ... ON CONFLICT DO UPDATE` 방식으로 적재합니다.

## Corpus Embedding Sync Script

관리자 API 대신 Python 스크립트로 corpus 임베딩을 일괄 동기화할 수 있습니다.

실행:

```bash
source .venv/bin/activate
pip install -r scripts/requirements-corpus-import.txt
python scripts/sync_corpus_embeddings.py --env-file .env
```

옵션 예시:

```bash
python scripts/sync_corpus_embeddings.py --env-file .env --limit 100
python scripts/sync_corpus_embeddings.py --env-file .env --job-only
python scripts/sync_corpus_embeddings.py --env-file .env --question-only --batch-size 16
```

- `.env`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `COHERE_API_KEY`를 사용합니다.
- 기본 모델은 `embed-v4.0`, 기본 배치 크기는 `32`입니다.
- `mock_job_posting_embeddings`, `mock_question_embeddings` 테이블에 `INSERT ... ON CONFLICT DO UPDATE` 방식으로 적재합니다.

## CI/CD

- `CI`: `main`, `develop` 브랜치 push 및 PR에서 테스트와 Docker 이미지 빌드를 실행합니다.
- `Deploy`: `main` 브랜치 push 또는 수동 실행 시 GHCR에 이미지를 푸시하고, 배포 서버 secret이 있으면 SSH로 `docker-compose.prod.yml`을 갱신합니다.

GitHub Actions 배포 secret:

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_PORT` optional, default `22`
- `DEPLOY_SSH_KEY`
- `DEPLOY_PATH`
- `GHCR_USERNAME`
- `GHCR_TOKEN`

## 배포 환경

백엔드 API는 Docker 이미지로 빌드되어 GHCR에 업로드되고, GitHub Actions를 통해 EC2 서버에 배포됩니다.

### 인프라

- 서버: AWS EC2 `t3.small`
- OS: Amazon Linux 2023
- DB: AWS RDS PostgreSQL `db.t3.micro`
- Redis: Upstash Redis
- 웹 서버: Nginx
- 컨테이너 런타임: Docker, Docker Compose

### 배포 흐름

1. `main` 브랜치에 변경사항이 반영됩니다.
2. GitHub Actions가 Docker 이미지를 빌드합니다.
3. 이미지를 GHCR에 푸시합니다.
4. GitHub Actions가 EC2에 SSH로 접속합니다.
5. EC2에서 `docker-compose.prod.yml`을 사용해 최신 이미지를 pull하고 API 컨테이너를 재기동합니다.

### 서버 배포 경로

```bash
/opt/jobdri-api
```

서버에는 아래 파일이 필요합니다.

```text
docker-compose.prod.yml
.env
```

## 운영 API 도메인

운영 API는 Nginx reverse proxy와 Let's Encrypt 인증서를 통해 HTTPS로 제공됩니다.

```text
https://api.jobdri.site
```

- Nginx는 80/443 요청을 Spring Boot API 컨테이너의 8080 포트로 프록시합니다.
- SSL 인증서는 Certbot으로 발급했으며 자동 갱신이 설정되어 있습니다.
- 상태 확인: `https://api.jobdri.site/actuator/health`

정상 응답:

```json
{"status":"UP","groups":["liveness","readiness"]}
```
