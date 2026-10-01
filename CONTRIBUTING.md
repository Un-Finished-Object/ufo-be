# 개발 참여 가이드

프로젝트의 기능과 실행 방법은 [README](README.md)에 정리되어 있습니다. 코딩 에이전트가 따라야 할 저장소 규칙은 [AGENTS.md](AGENTS.md)를 기준으로 합니다.

## 개발 환경

- JDK 21이 필요합니다. Docker Compose로 실행하면 MySQL 8.4 컨테이너를 사용합니다.
- [환경 변수 예시](.env.example)를 `.env`로 복사하고 `change-me`, `replace-with-...` 값을 개발 환경에 맞게 바꿉니다. `.env`와 실제 자격 증명은 커밋하지 않습니다.
- 로컬 실행과 Docker Compose 실행 순서는 [README의 로컬 개발 안내](README.md#로컬-개발)를 따릅니다.

## 코드와 테스트

업무 코드는 `src/main/java/com/ufo/ufo/domain/` 아래 도메인별로 두고, 공통 보안·예외·설정 코드는 `global/`에 둡니다. 도메인에서는 `api`, `application`, `dao`, `domain`, `dto` 계층을 사용합니다.

전체 테스트는 macOS·Linux에서 `./gradlew test`, Windows에서 `.\gradlew.bat test`로 실행합니다. `main` 대상 PR에서는 GitHub Actions의 `Gradle tests (JDK 21)` 검사가 같은 테스트를 실행하며, 실패 시 테스트 보고서를 아티팩트로 남깁니다. 코딩 에이전트가 컴파일이나 테스트 명령을 실행할 때는 [AGENTS.md의 승인 규칙](AGENTS.md#build-and-test-approval)을 먼저 따릅니다.

## 코드 스타일과 개발 컨벤션

새로 작성하거나 수정하는 Java 코드는 [우아한테크코스 Java Style Guide](https://github.com/woowacourse/woowacourse-docs/blob/main/styleguide/java/README.md)를 따릅니다. 이 가이드는 Google Java Style을 바탕으로 하며, 블록 들여쓰기는 공백 4칸, 줄 바꿈 후 이어지는 줄은 최소 8칸, 한 줄은 최대 120자로 정합니다. IDE에서 포맷을 맞출 때는 [우테코 IntelliJ 설정](https://github.com/woowacourse/woowacourse-docs/blob/main/styleguide/java/intellij-java-wooteco-style.xml)을 참고할 수 있습니다. 현재 Gradle에는 포맷 검사 작업이 없으므로 변경한 코드에 적용하고 무관한 파일을 일괄 재포맷하지 않습니다.

이 저장소의 코드를 작성할 때는 다음 관례를 따릅니다.

- 컨트롤러는 요청 매핑을 맡고 요청 본문은 `@Valid`로 검증하며, 응답은 `ApiResponse`로 감쌉니다. 엔티티를 그대로 응답하지 않고 `dto.request`와 `dto.response`를 구분합니다. 단순 DTO는 기존 코드처럼 `record`를 사용하고, 응답 변환이 필요하면 `from`·`of` 팩터리 메서드를 둡니다.
- 서비스는 `@RequiredArgsConstructor`와 `final` 필드로 의존성을 주입합니다. 조회 중심 서비스에는 `@Transactional(readOnly = true)`를 사용하고, 데이터를 바꾸는 메서드에는 `@Transactional` 경계를 둡니다. DB 조회·저장은 해당 도메인의 `dao`에 둡니다.
- JPA 엔티티는 `User`, `Pattern`처럼 보호된 기본 생성자를 두고, 상태 변경은 공개 setter보다 의미 있는 도메인 메서드로 표현합니다. 업무 오류는 기존 `ApiException` 계열과 공통 예외 응답 방식을 따릅니다.
- 테스트는 `*Test` 이름과 운영 코드의 패키지 경로를 따릅니다. `@DisplayName`에는 검증할 동작을 한국어로 적고, 반복되는 테스트 데이터는 `support/fixture`를 사용합니다.

## 이슈 작성

작업 전 코드와 열린·닫힌 이슈를 확인해 중복을 피합니다. 기능·리팩토링 작업은 해당 [이슈 템플릿](.github/ISSUE_TEMPLATE)을 사용합니다. 본문은 짧은 배경 문단, 구체적인 작업 3~5개, 기대 효과 1~2개를 기준으로 작성하고, API 요청·응답 형식이나 DB 저장처럼 팀에서 쓰는 말로 설명합니다. 리팩토링 이슈에는 유지할 기존 동작을 적고, 직접 이어지는 작업만 `후속 이슈: #번호`로 연결합니다.

우선순위 라벨 `🚨 P0`·`🔥 P1`·`⭐ P2` 중 하나와 작업 종류 라벨 하나를 붙입니다. 이슈를 등록한 뒤 제목·본문·라벨을 확인합니다.

## 브랜치와 커밋

작업 트리를 확인하고 이슈 브랜치가 있으면 재사용합니다. 새 브랜치는 `feature/#번호-작업_내용`, `fix/#번호-작업_내용`, `refactor/#번호-작업_내용`, `docs/#번호-작업_내용` 형식으로 만듭니다. 작업명은 이슈 제목의 핵심 내용을 짧게 옮기고 단어 사이에 밑줄을 넣습니다.

커밋 제목은 `fix: 토큰 용도 구분`처럼 `<prefix>: <target> <간결한 한국어 설명>` 형식을 사용합니다. 변경 목적에 따라 `feat`, `fix`, `refactor`, `test`, `docs`를 선택하고, 모델·서비스/API·테스트·마이그레이션 등 서로 독립적으로 검토할 수 있는 변경은 커밋을 나눕니다.

## PR 작성

PR 제목은 브랜치 유형의 첫 글자를 대문자로 바꾸고 작업명의 밑줄을 공백으로 바꿉니다. `docs/#123-에이전트_지침_및_개발_참여_가이드_관리`의 제목은 `Docs/#123 에이전트 지침 및 개발 참여 가이드 관리`입니다. 이슈 제목을 그대로 복사하거나 `[Docs]`처럼 대괄호 접두사를 붙이지 않습니다.

최근 브랜치 커밋을 확인한 뒤 [PR 템플릿](.github/PULL_REQUEST_TEMPLATE.md)에 맞춰 한국어로 변경 내용을 적고, 브랜치나 커밋의 이슈 번호를 `closes: #번호`에 넣습니다. 관련 API·DB 변경과 엔드포인트 예시 또는 REST Docs 갱신 내용은 필요할 때 함께 적습니다. `## 💬 기타`에는 리뷰어가 알아야 할 이슈별 맥락을 적고, 검증·배포·DB 변경 사항을 모아 두는 기본 칸으로 사용하지 않습니다.

PR에는 연결한 이슈의 모든 라벨을 동일하게 적용하고, 생성 후 제목·본문·라벨을 확인합니다. API 요청·응답이나 DB 구조가 달라지면 관련 문서도 함께 고칩니다.
