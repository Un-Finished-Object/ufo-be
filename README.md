# UFO Backend

> 대체 실 추천받고 온라인 뜨친이랑 프로젝트 완성하기

UFO는 도안을 찾고, 대체 실을 고르고, 같은 도안을 만드는 사람들과 이야기를 나누는 과정을 돕는 뜨개 커뮤니티 서비스입니다.

* **서비스**: https://www.knit-ufo.co.kr
* **Instagram**: https://www.instagram.com/ufoknitting
* **Frontend**: https://github.com/Un-Finished-Object/ufo-fe

<img width="1350" height="1001" alt="og-ufo" src="https://github.com/user-attachments/assets/8929742f-6cbf-47b3-a6f1-d9cf9d0ecfb3" />

---

## 목차

- [주요 기능](#주요-기능)
  - [도안 탐색](#도안-탐색)
  - [대체 실 추천](#대체-실-추천)
  - [실시간 커뮤니티](#실시간-커뮤니티)
  - [사용자와 리워드](#사용자와-리워드)
  - [프로필 이미지 업로드](#프로필-이미지-업로드)
- [기술 스택](#기술-스택)
- [주요 설계](#주요-설계)
  - [OAuth2와 JWT 인증](#oauth2와-jwt-인증)
  - [도안 구매와 크레딧](#도안-구매와-크레딧)
  - [실시간 채팅](#실시간-채팅)
  - [이미지 업로드](#이미지-업로드)
- [로컬 개발](#로컬-개발)
  - [요구 환경](#요구-환경)
  - [저장소 복제](#저장소-복제)
  - [환경 변수 설정](#환경-변수-설정)
  - [로컬 서버 실행](#로컬-서버-실행)
  - [Docker Compose 실행](#docker-compose-실행)
- [프로젝트 구조](#프로젝트-구조)
- [API](#api)
- [배포](#배포)

---

## 주요 기능

### 도안 탐색

* 카테고리별 도안 목록과 키워드 검색
* 인기순·최신순 정렬과 관심사 기반 추천
* 도안 상세, 원작 실, 이미지 정보 제공
* 사용자별 도안 조회 기록과 조회수 관리
* 도안 찜 및 내 찜 목록 관리

### 대체 실 추천

* 도안에 사용된 원작 실 세트 조회
* 원작 실별 대체 실과 유사도 항목 제공
* 사용자가 직접 등록한 대체 실 관리
* 대체 실 반응과 댓글 관리

### 실시간 커뮤니티

* 도안 구매자를 위한 기간별 채팅방 배정
* STOMP WebSocket 기반 실시간 메시지 송수신
* 답장, 메시지 삭제, 읽음 상태와 미확인 메시지 수 관리
* 사용자별 채팅방 즐겨찾기·숨김 설정
* 관리자용 미확인 채팅방 조회와 메시지 관리

### 사용자와 리워드

* Google·Kakao·Naver OAuth 로그인
* JWT Access Token 인증과 Refresh Token 재발급
* 회원가입, 프로필, 닉네임과 관심사 관리
* 출석 체크, 크레딧 적립·사용 내역 관리
* 채팅방과 대체 실 정보의 크레딧 기반 해금
* 친구 초대 코드 발급·등록과 쌍방 보상

### 프로필 이미지 업로드

* AWS S3 Presigned POST 발급
* 프로필 이미지용 객체 키 생성과 소유권 검증
* 파일 개수, 크기, 콘텐츠 타입과 객체 키 검증
* CDN URL 변환과 프로필 이미지 교체 후 객체 정리

---

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Framework | `Spring Boot 4` `Spring MVC` |
| Language | `Java 21` |
| Data | `Spring Data JPA` `Hibernate` `MySQL` |
| Security | `Spring Security` `OAuth2 Client` `JWT` |
| Realtime | `STOMP WebSocket` |
| Storage | `AWS S3` `CDN` |
| Validation | `Jakarta Validation` |
| Test | `JUnit 5` `Mockito` `MockMvc` |
| Build | `Gradle` `Asciidoctor` |
| Infra | `Docker` `Docker Compose` `GitHub Actions` `AWS EC2` |

---

## 주요 설계

### OAuth2와 JWT 인증

Google, Kakao, Naver OAuth2 로그인 뒤 Access Token과 Refresh Token을 발급합니다.

* Access Token은 REST API의 `Authorization: Bearer` 헤더로 전달
* Refresh Token은 HttpOnly Cookie로 관리
* 서버는 세션을 생성하지 않는 Stateless 방식으로 동작
* Spring Security Filter에서 Access Token 검증 및 인증 정보 구성
* `@LoginUser` Argument Resolver로 현재 사용자를 Controller에 주입
* 일반 사용자와 관리자 API 권한 분리

WebSocket 연결에서도 STOMP `CONNECT` 프레임의 Access Token을 검증합니다. HTTP 요청과 실시간 연결은 같은 인증 기준을 사용합니다.

---

### 도안 구매와 크레딧

채팅방과 대체 실 정보는 크레딧을 사용해 해금합니다.

구매 요청이 들어오면 도안과 사용자 상태를 확인한 뒤 크레딧을 차감하고, 거래 내역과 해금 정보를 저장합니다. 채팅방을 구매하면 참여할 채팅방을 배정하고 해당 채팅방에서 쓸 닉네임도 만듭니다.

* 회원가입·출석·친구 초대를 통한 크레딧 적립
* 일반 적립에 대한 일일 한도 적용
* 사용자·도안·해금 유형별 중복 구매 방지
* 사용자 잔액과 크레딧 거래 내역 분리 저장
* DB 유일 제약과 비관적 락을 이용한 중복 요청 제어

---

### 실시간 채팅

채팅은 도안을 기준으로 만든 채팅방에서 진행합니다. 채팅방은 정해진 기간을 기준으로 나누고, 메시지 전송과 읽음 상태 갱신은 STOMP로 처리합니다.

* WebSocket endpoint: `/ws/chat`
* Client publish prefix: `/pub`
* Server subscribe prefix: `/sub`
* 메시지 전송: `/pub/chat/message`
* 읽음 상태 갱신: `/pub/chat/read`
* 채팅방 구독: `/sub/chat/rooms/{roomId}`

토큰이 없거나 유효하지 않으면 연결을 거부합니다. 일반 사용자는 참여한 활성 채팅방만 구독할 수 있으며, 관리자는 관리자 권한을 확인한 뒤 활성 채팅방을 구독할 수 있습니다. 클라이언트 전송은 위의 두 `/pub` 경로만 허용합니다. 권한 없는 구독이나 허용하지 않은 경로로의 전송은 STOMP `ERROR` 응답 후 연결을 종료합니다.

이전 메시지 조회, 채팅방 설정 변경, 관리자 기능은 REST API로 제공합니다.

---

### 이미지 업로드

클라이언트는 백엔드가 발급한 Presigned POST 정보로 프로필 이미지를 S3에 직접 업로드합니다.

* 지원 형식: JPEG, PNG, WebP
* 파일당 최대 크기: 10 MiB
* 요청당 최대 파일 수: 5개
* 프로필 이미지 목적과 객체 키 검증
* 저장된 객체 키를 CDN URL로 변환
* 프로필 변경 트랜잭션 커밋 후 이전 이미지 정리

이미지 파일은 애플리케이션 서버를 거치지 않습니다. 서버는 객체 키가 사용자 프로필 이미지 경로와 형식에 맞는지 검증합니다.

---

## 로컬 개발

### 요구 환경

* JDK 21
* MySQL 8
* Docker 및 Docker Compose — 컨테이너로 실행하는 경우

### 저장소 복제

```bash
git clone https://github.com/Un-Finished-Object/ufo-be.git
cd ufo-be
```

### 환경 변수 설정

프로젝트 루트에 `.env` 파일을 만들고 데이터베이스, JWT, OAuth, S3 설정을 개발 환경에 맞게 입력합니다.

```bash
touch .env
```

PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
New-Item .env -ItemType File
```

주요 환경 변수는 다음과 같습니다.

| 구분 | 환경 변수 |
| --- | --- |
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| JWT | `SPRING_JWT_SECRET`, `SPRING_JWT_ACCESS_TOKEN_EXPIRE`, `SPRING_JWT_REFRESH_TOKEN_EXPIRE` |
| OAuth2 | Google·Kakao·Naver Client ID와 Client Secret |
| Redirect/CORS | `OAUTH_REDIRECT_URL`, `OAUTH_SIGNUP_REDIRECT_URL`, `OAUTH_ADMIN_REDIRECT_URL`, `OAUTH_COOKIE_DOMAIN`, `DOMAIN_URL` |
| Image/S3 | `S3_IMAGE_BUCKET`, `S3_REGION`, `S3_PUBLIC_BASE_URL`, `CDN_BASE_URL`, `DEFAULT_PROFILE_IMAGE_KEY` |
| Referral | `REFERRAL_HMAC_SECRET` |

실제 비밀번호, JWT Secret과 OAuth Client Secret은 저장소에 커밋하지 않습니다.

### 로컬 서버 실행

`.env`의 값을 현재 셸의 환경 변수로 불러온 뒤 애플리케이션을 실행합니다.

```bash
set -a
source .env
set +a
./gradlew bootRun
```

PowerShell에서는 다음 명령을 사용합니다.

```powershell
Get-Content .env | Where-Object { $_ -match '^[^#].+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

.\gradlew.bat bootRun
```

기본 서버 주소는 `http://localhost:8080`입니다.

### Docker Compose 실행

Dockerfile은 빌드된 JAR를 이미지에 복사하므로 먼저 `bootJar`를 생성합니다.

```bash
./gradlew bootJar
docker compose --env-file .env up --build
```

Docker Compose는 백엔드와 MySQL을 함께 실행하며 기본적으로 호스트의 `127.0.0.1`에만 포트를 바인딩합니다.

---

## 프로젝트 구조

```text
src/
├── main/
│   ├── java/com/ufo/ufo/
│   │   ├── domain/                  # 기능 도메인
│   │   │   ├── alternative/        # 대체 실 반응·댓글
│   │   │   ├── attendance/         # 출석 체크
│   │   │   ├── auth/               # 회원가입·토큰·OAuth 진입점
│   │   │   ├── chat/               # 채팅방·메시지·WebSocket
│   │   │   ├── credit/             # 크레딧·거래 내역·해금
│   │   │   ├── image/              # S3 이미지 업로드
│   │   │   ├── interest/           # 관심사
│   │   │   ├── pattern/            # 도안·실·대체 실
│   │   │   ├── referral/           # 친구 초대
│   │   │   ├── scrap/              # 도안 찜
│   │   │   └── user/               # 사용자·프로필·프로젝트
│   │   └── global/                  # 보안·설정·예외·공통 응답·검증
│   └── resources/
│       └── application.yaml         # 공통 애플리케이션 설정
└── test/java/com/ufo/ufo/
    ├── domain/                      # 도메인별 Controller·Service 테스트
    ├── global/                      # OAuth·JWT·WebSocket 보안 테스트
    └── support/fixture/             # 공통 테스트 Fixture
```

각 도메인은 필요에 따라 다음 계층으로 구성됩니다.

```text
api/          # REST·WebSocket Controller
application/  # 유스케이스와 트랜잭션 조율
dao/          # Spring Data JPA Repository
domain/       # Entity, Enum, Value 정책
dto/          # 요청·응답 모델
exception/    # 도메인 예외
validation/   # 커스텀 요청 검증
```

---

## API

REST API는 `/v1` 경로 아래에 제공됩니다.

| 영역 | 기본 경로 | 주요 기능 |
| --- | --- | --- |
| 인증 | `/v1/auth`, `/v1/auth/login` | OAuth 로그인, 회원가입, 토큰 재발급, 로그아웃 |
| 사용자 | `/v1/users` | 내 정보, 닉네임 검사, 관심사, 찜·채팅·프로젝트 목록 |
| 도안 | `/v1/patterns` | 목록, 검색, 추천, 상세, 조회수, 구매, 찜, 대체 실 등록 |
| 실 | `/v1/yarns` | 실 상세와 원작 실별 대체 실 조회 |
| 대체 실 커뮤니티 | `/v1/alternatives` | 반응과 댓글 조회·등록·수정·삭제 |
| 채팅 | `/v1/chat` | 메시지 조회와 채팅방 상태 변경 |
| 관리자 채팅 | `/v1/admin/chats` | 미확인 채팅방과 메시지 관리 |
| 이미지 | `/v1/images` | S3 Presigned POST 발급 |
| 출석 | `/v1/attendance` | 출석 체크와 월별 현황 |
| 크레딧 | `/v1/credits` | 지갑, 거래 내역과 정책 조회 |
| 친구 초대 | `/v1/referral` | 초대 코드 조회와 등록 |
| 관심사 | `/v1/interest` | 관심사 키워드 조회 |

응답은 공통적으로 다음 형태를 사용합니다.

```json
{
  "data": {},
  "error": null
}
```

오류가 발생하면 `data`는 `null`이고 `error`에 코드와 메시지가 포함됩니다.

---

## 배포

`main` 브랜치에 코드가 push되면 GitHub Actions가 다음 과정을 실행합니다.

1. JDK 21 환경에서 테스트 실행
2. Spring Boot JAR 생성
3. Docker 이미지를 커밋 SHA와 `latest` 태그로 빌드
4. Docker Hub에 이미지 push
5. EC2에서 대상 이미지를 pull하고 Docker Compose로 재기동

운영용 `application-prod.yml`과 Docker Compose 설정은 EC2 배포 디렉터리에서 별도로 관리합니다.

배포에는 다음 GitHub Secrets가 필요합니다.

* `DOCKER_USERNAME`
* `DOCKER_PASSWORD`
* `EC2_HOST`
* `EC2_USERNAME`
* `EC2_SSH_KEY`
