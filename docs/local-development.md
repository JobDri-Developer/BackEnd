# 로컬 실행

로컬 Docker 실행은 `docker-compose.yml`과 Spring `dev` 프로필을 사용한다.
운영 실행은 `docker-compose.prod.yml`과 `prod` 프로필을 사용한다.

## Docker로 실행

`BackEnd`와 `analysis-server` 저장소를 같은 상위 폴더에 둔다.
`BackEnd`의 `.env.example`을 참고해 `.env`를 준비한다.

```bash
./gradlew bootJar
docker compose -f docker-compose.yml up -d --build
```

API Dockerfile은 `build/libs`의 JAR를 복사하므로 코드 변경 후 `bootJar`를 먼저 실행한다.
Swagger: <http://localhost:8080/swagger-ui/index.html>

Compose는 기본적으로 DB·Redis·RabbitMQ 주소를 컨테이너 이름으로 지정한다.
`application-dev.yaml`의 `localhost` 기본값은 앱을 호스트에서 직접 실행할 때 사용한다.

## 환경변수와 기존 DB

- `.env`에는 API 키와 기본값을 바꿔야 하는 설정을 보관한다. 파일은 Git에 커밋하지 않는다.
- 현재 DB에 맞는 `DB_USERNAME`, `DB_PASSWORD`, `POSTGRES_DB`는 유지한다.
- PostgreSQL의 초기 사용자·비밀번호·DB 생성 설정은 빈 볼륨에만 적용된다.
  `.env`에서 값을 삭제하거나 변경해도 기존 DB의 계정은 변경되지 않는다.
- 로컬 Compose의 API DB URL 기본값은 `jdbc:postgresql://postgres:5432/${POSTGRES_DB:-jobdri}`다.
- `.env` 또는 셸 환경변수에 `DB_URL`을 지정하면 기본값 대신 해당 주소를 사용한다.
  외부 PostgreSQL은 `jdbc:postgresql://<DB 호스트>:<포트>/<DB 이름>`으로 지정한다.
  Mac 호스트의 다른 PostgreSQL은 `jdbc:postgresql://host.docker.internal:<포트>/<DB 이름>`으로 지정한다.
  컨테이너의 `localhost`는 컨테이너 자신을 가리키므로 호스트 DB 주소로 사용하지 않는다.
- `.env`는 Compose의 필수 `env_file`이므로 파일 전체를 삭제하지 않는다.
- DB 데이터를 유지할 때는 `docker compose down -v`로 볼륨을 삭제하지 않는다.

앱을 직접 실행하면 Spring의 기본 프로필은 `dev`다.
Compose의 `.env` 자동 읽기와 호스트에서 실행하는 JVM의 환경변수 전달은 별개이므로,
IDE나 실행 환경에서 필요한 값을 전달해야 한다.

호스트에서 실행하는 API를 Compose PostgreSQL에 연결하려면 다음 환경변수를 IDE 실행 설정이나 셸에 전달한다.
Compose PostgreSQL은 호스트의 `5433` 포트를 사용하며, `DB_USERNAME`과 `DB_PASSWORD`도 기존 DB 계정에 맞춰 전달한다.

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://localhost:5433/jobdri
```

`POSTGRES_DB`를 변경했다면 위 URL의 DB 이름도 함께 변경한다.
