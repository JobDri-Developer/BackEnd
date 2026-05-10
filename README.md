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
