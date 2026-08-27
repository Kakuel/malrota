# 배포 안내: GitHub Pages + Railway

말로타의 프론트엔드는 GitHub Pages에서 정적 파일로 제공하고, Spring Boot API와 PostgreSQL은 Railway에서 실행합니다.

## 1. Railway: PostgreSQL과 백엔드

1. Railway에서 새 프로젝트를 만들고 **PostgreSQL** 서비스를 추가합니다.
2. 같은 프로젝트에서 **GitHub Repo**로 `Kakuel/malrota`를 연결하고, 배포 브랜치는 `main`으로 선택합니다.
3. 이 저장소에는 최상위 `Dockerfile`이 포함되어 있습니다. **Root Directory는 비워 두고**, Railway가 이 Dockerfile을 감지하도록 둡니다. 이 방식은 모노레포의 `backend`만 Gradle로 빌드하므로 Railpack이 저장소 최상위를 분석하다 실패하는 문제를 피합니다.
4. **Railway Config File** 항목도 비워 둡니다. `/backend/railway.toml`을 입력할 필요가 없습니다.
5. 백엔드 서비스의 Variables에 아래 값을 추가합니다. `Postgres`는 Railway에서 생성한 DB 서비스 이름이며, 다른 이름으로 바꿨다면 그 이름을 사용합니다.

```text
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
JPA_DDL_AUTO=update
CORS_ALLOWED_ORIGINS=https://kakuel.github.io

WATSONX_ENABLED=true
WATSONX_API_KEY=<watsonx API 키>
WATSONX_PROJECT_ID=<watsonx 프로젝트 ID>
WATSONX_URL=https://us-south.ml.cloud.ibm.com
WATSONX_MODEL_ID=mistralai/mistral-small-3-1-24b-instruct-2503

IBM_STT_ENABLED=true
IBM_STT_URL=<IBM STT URL>
IBM_STT_API_KEY=<IBM STT API 키>
IBM_TTS_ENABLED=true
IBM_TTS_URL=<IBM TTS URL>
IBM_TTS_API_KEY=<IBM TTS API 키>

TAGO_ENABLED=true
TAGO_SERVICE_KEY=<TAGO 서비스 키>
```

6. Railway 서비스의 **Settings > Networking**에서 도메인을 생성합니다.
7. `https://<Railway-도메인>/api/health`가 `UP` 응답을 반환하는지 확인합니다.

`PORT`는 Railway가 자동으로 주입하므로 직접 입력하지 않습니다. 실제 API 키와 DB 비밀번호는 GitHub에 커밋하지 않습니다.

## 2. GitHub Pages: 프론트엔드

1. GitHub 저장소의 **Settings > Secrets and variables > Actions > Variables**에서 아래 저장소 변수를 추가합니다.

```text
REACT_APP_API_BASE_URL=https://<Railway-도메인>
```

끝의 `/`나 `/api`는 넣지 않습니다.

2. GitHub 저장소의 **Settings > Pages > Build and deployment > Source**를 **GitHub Actions**로 바꿉니다.
3. 이 배포 설정이 `main`에 병합된 뒤 push되면 `Deploy frontend to GitHub Pages` 워크플로가 실행됩니다.
4. 배포 주소 `https://kakuel.github.io/malrota/`에서 버스 조회, 음성 API, 예매 내역 저장을 확인합니다.

## 배포 후 점검

- 브라우저 개발자 도구 Network 탭에서 요청 대상이 `localhost`가 아니라 Railway 도메인인지 확인합니다.
- Railway 로그에서 `Tomcat started on port`와 PostgreSQL 연결 오류가 없는지 확인합니다.
- GitHub Pages 접속 시 CORS 오류가 나면 Railway의 `CORS_ALLOWED_ORIGINS`가 정확히 `https://kakuel.github.io`인지 확인한 뒤 재배포합니다.
- Railway DB의 `bookings` 및 `booking_history_view`에서 새 예매 내역이 저장되는지 확인합니다.
