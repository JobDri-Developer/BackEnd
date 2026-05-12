# BackEnd
Repository of JobDri BackEnd

## Docker

로컬 실행:

```bash
cp .env.example .env
docker compose up --build
```

배포 서버 실행:

```bash
cp .env.production.example .env
docker compose -f docker-compose.prod.yml up -d
```

`prod` 프로필은 `/actuator/health`를 노출합니다.

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
