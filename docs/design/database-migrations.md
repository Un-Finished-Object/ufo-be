# DB 스키마 변경 이력 관리 도입

[#136](https://github.com/Un-Finished-Object/ufo-be/issues/136)에 따라 MySQL 스키마 변경을 Flyway SQL로 관리한다.

## 적용 방식

- `prod`에서 애플리케이션 시작 시 JPA 초기화 전에 마이그레이션을 실행한다. Hibernate의 자동 스키마 변경은 끈다.
- 새 DB는 V1부터 적용한다. 기존 DB는 스키마 비교와 백업 복원 검증을 마친 뒤 `flywayBaseline`으로 버전 1을 등록하고 V2부터 적용한다. 자동 baseline은 사용하지 않는다.
- 적용한 SQL은 수정하지 않고 다음 버전의 파일을 추가한다. 적용 이력과 체크섬은 `flyway_schema_history`에서 관리한다.
- 변경된 DB는 이전 애플리케이션과도 호환돼야 한다. 컨테이너를 복구해도 DB 변경은 되돌아가지 않는다.

## 초기 SQL

`V1__initial_schema.sql`은 기존 DB의 테이블 21개 정의를 담는다. `patterns`의 긴 문자열 컬럼과 현재 사용하지 않는 `yarn_id` 컬럼도 유지한다. 데이터와 현재 AUTO_INCREMENT 값은 포함하지 않는다.

V2는 `chat_read_statuses`의 사용자와 채팅방 조합에 UNIQUE가 없을 때만 추가한다. 이름이나 열 순서가 달라도 같은 조합이면 기존 제약을 유지한다. 중복 데이터는 자동 삭제하지 않으며 [읽음 데이터 정리 절차](../database/chat-read-status.md)에 따라 먼저 정리한다.

V3는 프로필 이미지 태그 변경과 삭제를 재시도할 `profile_image_tasks` 테이블을 추가한다.

## 테스트

| DB | 스키마 초기화 |
| --- | --- |
| MySQL | Flyway V1부터 V3까지 적용. 읽음 데이터 정리 테스트는 제약 없는 테이블을 별도로 생성 |
| PostgreSQL, H2 | Flyway를 끄고 Hibernate `create-drop` 사용 |

신규 DB 생성, 기존 데이터 보존, 수동 제약 유지, SQL 중복 실행과 체크섬 오류, 적용 실패 후 재시도를 검증한다. 실행과 복구 절차는 [스키마 마이그레이션 안내](../database/schema-migrations.md)를 따른다.
