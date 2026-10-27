# MySQL 스키마 마이그레이션

`prod`에서는 애플리케이션 시작 시 Flyway를 실행한다. 새 DB는 V1부터 적용하며, 기존 DB는 baseline을 등록한 뒤 V2부터 적용한다.

## 기존 DB 최초 도입

1. 대상 DB와 계정 권한을 확인하고, 스키마와 데이터를 백업해 별도 DB에서 복원을 검증한다.
2. [조회 SQL](../../scripts/database/inspect-schema.sql)을 실행해 [V1](../../src/main/resources/db/migration/mysql/V1__initial_schema.sql)과 비교한다. 읽음 데이터가 중복돼 있으면 쓰기를 중단하고 [정리 절차](chat-read-status.md)를 수행한다.
3. 백업 복원과 스키마 비교를 마친 뒤 아래 명령으로 baseline 버전 1을 등록한다. Flyway의 baseline 작업은 스키마 일치를 검사하지 않는다.
4. 등록 성공을 확인한 뒤 배포한다. readiness 확인을 마치면 쓰기를 재개한다.

JDK 21 환경에서 대상 DB의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정한다. 빈 DB에는 baseline을 등록하지 않고 바로 배포한다.

```powershell
.\gradlew.bat flywayBaseline --no-daemon
```

Bash:

```shell
./gradlew flywayBaseline --no-daemon
```

## SQL 추가

이미 적용한 SQL은 수정하지 않는다. 변경 사항은 다음 버전의 SQL 파일로 추가한다. 컬럼과 테이블을 먼저 추가하고, 삭제나 이름 변경은 이전 버전의 애플리케이션을 더 이상 사용하지 않을 때 별도로 진행한다.

## 적용 실패 시 조치

- 추가 배포를 중단하고 실패한 SQL, 실제 스키마와 `flyway_schema_history`를 확인한다. 체크섬 오류라면 수정된 SQL을 원본으로 복원한다.
- [배포 롤백 절차](../deployment/rollback.md)에 따라 이전 컨테이너가 복구됐는지 확인한다. MySQL DDL은 일부만 반영될 수 있으며, 컨테이너를 복구해도 DB 변경은 되돌아가지 않는다.
- 원인을 해결하기 전에는 재실행하거나 `repair`, `clean`을 실행하지 않는다. 이전 버전도 동작하지 않으면 데이터 손실 범위를 확인한 뒤 백업 복원을 검토한다.

세부 동작과 테스트 정책은 [마이그레이션 설계](../design/database-migrations.md), 테스트 명령은 [개발 참여 가이드](../../CONTRIBUTING.md#통합-테스트-실행)를 참고한다.
