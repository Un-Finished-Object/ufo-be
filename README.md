# UFO Backend

UFO는 뜨개 도안 탐색·구매, 실 대체 추천, 커뮤니티 채팅을 제공하는 서비스의 백엔드 API 서버입니다.

## 기술 스택

- Java 21, Spring Boot 4, Gradle
- Spring MVC, Validation, Spring Data JPA, MySQL
- Spring Security, OAuth2 Client, JWT
- STOMP WebSocket
- AWS S3 Presigned URL, CDN URL 변환
- JUnit 5, MockMvc, Spring REST Docs
- Docker, Docker Compose, GitHub Actions

## 주요 기능

- OAuth 로그인, JWT 재발급·로그아웃, 회원가입 및 내 정보 관리
- 도안 목록·검색·상세·구매·찜 및 관심사 기반 추천
- 원작 실과 대체 실 조회, 대체 실 반응·댓글 관리
- 구매자 기반 채팅방, 실시간 메시지, 읽음 상태, 답장, 미확인 메시지 수
- 프로필·채팅 이미지의 S3 Presigned URL 업로드와 객체 키 기반 이미지 관리
- 출석 및 크레딧, 친구 초대 코드

## 프로젝트 구조

```text
src/main/java/com/ufo/ufo
├── domain/        # 기능 도메인별 API, 서비스, 엔티티, DTO, Repository
│   ├── alternative/ # 대체 실 세트의 반응, 댓글 및 권한 처리
│   ├── attendance/  # 월별 출석 현황과 일일 출석 체크
│   ├── auth/        # 회원가입, 토큰 재발급·로그아웃, OAuth 로그인 진입점
│   ├── chat/        # 채팅방 배정, 메시지·답장·읽음 상태, WebSocket 처리
│   ├── credit/      # 크레딧 지갑, 거래 내역, 정책 조회
│   ├── image/       # Presigned URL 발급, 객체 키 검증, CDN URL 변환
│   ├── interest/    # 관심사 키워드 조회 및 사용자 관심사 관리
│   ├── pattern/     # 도안·실 조회, 구매·찜, 원작 실·대체 실 추천
│   ├── referral/    # 친구 초대 코드 생성·저장·조회
│   ├── scrap/       # 도안 찜 영속화와 찜 목록 조회
│   └── user/        # 사용자 프로필, 닉네임, 구매 프로젝트, 관심사
└── global/        # 보안, 예외 처리, 설정, 공통 응답, 검증

src/test/java/com/ufo/ufo
└── support/fixture/ # 테스트용 Fixture
```

## 시작하기

### 요구 사항

- JDK 21
- MySQL
- Docker 및 Docker Compose (컨테이너 실행 시)

### 로컬 실행

1. 예시 파일을 복사해 로컬 환경 파일을 만듭니다.

```powershell
Copy-Item .env.example .env
```

2. `.env`에서 MySQL, JWT, OAuth, S3 값을 로컬 환경에 맞게 채웁니다. `.env`는 Git에서 제외됩니다.
3. `.env` 값을 현재 PowerShell 세션의 환경 변수로 로드합니다.

```powershell
Get-Content .env | Where-Object { $_ -match '^[^#].+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
```

4. 애플리케이션을 실행합니다.

```powershell
.\gradlew.bat bootRun
```

macOS/Linux에서는 다음 명령을 사용합니다.

```bash
cp .env.example .env
# .env 값을 환경에 맞게 수정한 뒤
set -a
source .env
set +a
./gradlew bootRun
```

기본 포트는 `8080`입니다.

## 설정

기본 설정은 [application.yaml](src/main/resources/application.yaml)에 있습니다. 운영 환경에서는 Docker Compose가 `/app/config/application-prod.yml`을 읽도록 구성되어 있습니다.

주요 외부 설정 항목은 다음과 같습니다.

| 구분 | 항목 |
| --- | --- |
| Database | Spring datasource 연결 정보 |
| Authentication | JWT 설정, OAuth2 provider client 설정 |
| OAuth redirect | `OAUTH_REDIRECT_URL`, `OAUTH_SIGNUP_REDIRECT_URL`, `OAUTH_COOKIE_DOMAIN` |
| CORS | `DOMAIN_URL` |
| Image/S3 | `CDN_BASE_URL`, `S3_IMAGE_BUCKET`, `S3_REGION`, `S3_PUBLIC_BASE_URL`, `DEFAULT_PROFILE_IMAGE_KEY` |
| Referral | `REFERRAL_HMAC_SECRET` |

S3 이미지 업로드는 JPEG, PNG, WebP 형식을 지원하며, 파일당 최대 크기는 10 MiB, 요청당 최대 파일 수는 5개입니다.

`.env.example`은 로컬 실행에 필요한 키 목록과 형식만 제공합니다. 실제 OAuth client secret, JWT secret, S3 버킷 및 DB 비밀번호는 각자의 로컬 환경 값으로 교체해야 합니다.

## 테스트 및 빌드

```powershell
# 테스트
$env:SPRING_PROFILES_ACTIVE = "test"
.\gradlew.bat test

# 전체 빌드
$env:SPRING_PROFILES_ACTIVE = "test"
.\gradlew.bat clean build

# REST Docs 생성
$env:SPRING_PROFILES_ACTIVE = "test"
.\gradlew.bat asciidoctor
```

macOS/Linux에서는 `gradlew.bat` 대신 `./gradlew`를 사용합니다.

`test` 프로필은 H2 인메모리 데이터베이스와 테스트용 OAuth/JWT 설정을 사용하므로, 테스트 실행에 로컬 MySQL이나 실제 OAuth 자격 증명이 필요하지 않습니다.

## API 구성

API는 `/v1` 경로 아래에 제공됩니다.

| 영역 | 기본 경로 |
| --- | --- |
| 인증 | `/v1/auth`, `/v1/auth/login` |
| 사용자 | `/v1/users` |
| 도안 | `/v1/patterns` |
| 실 | `/v1/yarns` |
| 대체 실 반응·댓글 | `/v1/alternatives` |
| 채팅 | `/v1/chat` |
| 이미지 | `/v1/images` |
| 출석·크레딧 | `/v1/attendance`, `/v1/credits` |

세부 요청·응답은 테스트 실행 후 생성되는 Spring REST Docs 산출물을 참고합니다.

## 배포

`main` 브랜치 push 시 GitHub Actions가 다음 순서로 동작합니다.

1. JDK 21 환경에서 테스트 실행 및 JAR 빌드
2. Docker 이미지를 커밋 SHA 태그와 `latest` 태그로 빌드·푸시
3. EC2에서 해당 SHA 이미지를 pull하고 Docker Compose로 재기동

배포에는 `DOCKER_USERNAME`, `DOCKER_PASSWORD`, `EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY` GitHub Secrets가 필요합니다. 운영용 `application-prod.yml`은 서버 외부에서 관리합니다.

`docker-compose.yml`은 로컬 개발용 백엔드·MySQL 구성을 정의합니다. `.env`에서 DB 자격 증명, 도메인 및 S3 설정을 주입하며, 백엔드와 MySQL 포트는 기본적으로 `127.0.0.1`에만 바인딩됩니다.

현재 Dockerfile은 빌드된 JAR를 입력으로 사용하므로, Compose 실행 전 JAR를 생성합니다.

```powershell
.\gradlew.bat bootJar
docker compose --env-file .env up --build
```

## License

[LICENSE](LICENSE)를 참고하세요.
