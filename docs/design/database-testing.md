# 공통 DB 테스트 설계

## 적용 범위

DB 실행과 접속, JPA 구성은 Spring Boot 테스트 자동 설정에 맡긴다.
JDBC, JPA Repository, 서비스 통합 테스트는 공통 설정을 가져와 DB를 선택하고,
같은 테스트 코드를 MySQL과 PostgreSQL에서 실행한다.

운영 DB는 MySQL로 유지한다.
이 테스트만으로 전체 기능의 PostgreSQL 운영 지원이나 다른 DB 지원을 보장하지 않는다.

컨테이너 빈에는 DB별 이미지와 옵션만 선언하고, 시작·종료와 접속 정보 전달에는
Spring Boot의 `@ServiceConnection`과 테스트 자동 설정을 사용한다.

## 공통 테스트 설정

공통 설정은 `src/test/java/com/ufo/ufo/support/database`에 모아 둔다.

- DB 선택: Gradle에서 `mysql`, `postgres`만 허용하고 테스트 JVM의 `ufo.test.database` 속성으로 전달한다.
  다른 값이나 빈 문자열을 지정하면 컨테이너를 시작하기 전에 오류가 발생한다.
- 컨테이너 선언: `TestDatabaseConfig`에 `MySQLContainer`와 `PostgreSQLContainer` 타입의 빈을 둔다.
  각각 `@ServiceConnection`과 `@ConditionalOnProperty`를 사용한다. 기본값은 MySQL이며,
  PostgreSQL을 선택하면 PostgreSQL 빈만 생성한다.
- 테스트 의존성: `spring-boot-testcontainers`, 두 DB의 Testcontainers 모듈과 JDBC 드라이버를 사용한다.
  PostgreSQL 드라이버는 `testRuntimeOnly`로 추가한다. 버전은 Spring Boot 의존성 관리에 따른다.
- 접속 설정: Spring Boot가 컨테이너의 연결 정보로 DataSource를 자동 설정한다.
  직접 만든 DataSource 빈, 외부 JDBC URL, 운영 DB 환경 변수는 사용하지 않는다.
- 테스트 애플리케이션: `DatabaseTestApplication`은 Boot 설정과 도메인 패키지 위치만 제공한다.
  운영 애플리케이션의 전체 컴포넌트 스캔을 사용하지 않는다.
- JDBC 테스트: `@JdbcTest`와 `@AutoConfigureTestDatabase(replace = NONE)`으로 실제 컨테이너에 연결한다.
  JPA나 도메인 서비스를 불러오지 않는다.
- JPA·서비스 테스트: `@DataJpaTest`와 함께 DB를 교체하지 않는 설정을 사용한다. EntityManagerFactory,
  JPA 트랜잭션 관리자와 Repository 구성은 Boot에 맡긴다. 생성·수정 시각 기록에는 `JpaAuditingTestConfig`를 사용한다.
- 서비스별 설정: 필요한 서비스는 각 테스트에서 추가하고, 외부 시스템을 호출하는 객체는 테스트용 객체로 바꾼다.
  공통 DB 설정에는 크레딧·인증·이미지 서비스 의존성을 넣지 않는다.

테스트 전용 DB 이름과 임의로 배정한 호스트 포트를 사용하며 영구 볼륨은 만들지 않는다.
각 DB의 데이터 경로에 맞게 메모리 저장 옵션을 지정한다.
두 DB에 테스트용 시간대와 잠금 대기 제한을 설정하고, MySQL에는 문자셋도 지정한다.
테스트는 `spring.config.name=application-db-test`로 테스트 전용 설정만 읽는다.
JPA의 `create-drop`은 `src/test/resources/application-db-test.yaml`에 설정한다.
테이블·열 이름에는 Boot의 기본 규칙을 적용하고, 운영 프로필이나 비밀 설정을 가져오지 않는다.
`@DirtiesContext(classMode = AFTER_CLASS)`를 지정해 테스트 클래스 실행이 끝나면 컨텍스트를 닫는다.
Spring Boot가 DataSource·JPA 빈을 정리한 뒤 컨테이너를 종료하므로 `start`·`stop` 메서드를 직접 지정하지 않는다.
컨테이너를 재사용하거나 전역 싱글턴으로 두지 않는다.

JDBC·JPA 테스트에서는 기본적으로 테스트 전체를 트랜잭션으로 감싼다.
현재 JDBC·Repository·동시성 테스트는 기본 롤백 대신 실제 커밋·롤백을 확인할 수 있도록
`@Transactional(propagation = NOT_SUPPORTED)`로 이 트랜잭션을 끈다.
준비 데이터를 커밋한 뒤 각 작업의 `TransactionTemplate`이나 JDBC 연결에서 커밋·롤백을 검증한다.
일반 Repository 테스트는 필요에 따라 테스트가 끝날 때 자동으로 롤백하는 기본 설정을 그대로 사용할 수 있다.

## 테스트 실행을 분리한 이유

Docker 없이 기본 테스트를 실행할 수 있도록 컨테이너가 필요한 통합 테스트를 따로 둔다.
통합 테스트에는 DB 이름 대신 공통 `integration` 태그를 붙인다.
실행할 DB는 `testDatabase` 속성으로 선택하고, 그 값을 별도의 테스트 JVM에 전달한다.
DB를 지정하지 않았을 때는 현재 운영 DB와 같은 MySQL을 기본값으로 사용한다.

| 작업 | 실행 대상 |
| --- | --- |
| `test` | 외부 인프라 없이 실행하는 기본 테스트. `integration` 태그 제외 |
| `integrationTest` | `integration` 태그가 붙은 모든 테스트. DB는 `testDatabase` 속성으로 선택 |

실행 명령과 새 테스트를 추가하는 방법은 [개발 참여 가이드](../../CONTRIBUTING.md#통합-테스트-실행)를 참고한다.
DB 선택값도 Gradle이 재실행 여부를 판단할 때 사용한다. 결과·보고서 디렉터리에는 DB 이름을 붙인다.
두 DB를 차례로 실행해도 먼저 실행한 DB의 보고서를 덮어쓰지 않는다.
Docker에 연결할 수 없거나 컨테이너가 시작되지 않으면 테스트는 실패한다.
다른 DB로 대체하거나 건너뛰지 않는다.

`integrationTest`를 `check`나 `build`에 연결하지 않아 기본 빌드에는 Docker가 필요하지 않다.
통합 테스트는 명시적으로 실행하거나 PR 검사에서 실행한다.

## 테스트에서 확인하는 내용

1. JDBC 테스트: 공통 DataSource로 선택한 DB에 연결해 종류를 확인한다.
   테스트 전용 테이블에서 PreparedStatement CRUD와 명시적인 롤백을 검증한다.
   JPA 없이 공통 설정을 사용할 수 있는지도 확인한다.
2. Repository 테스트: 실제 JPA Repository로 사용자를 잠근 뒤 최신 상태를 다시 읽는지 확인한다.
   같은 트랜잭션에서 다시 잠금을 요청해도 아직 저장하지 않은 변경이 유지되는지 검증한다.
   생성·수정 시각이 기록되는지도 확인한다.
3. 서비스 통합 테스트: 크레딧 동시성·롤백을 두 DB에서 동일하게 검증한다.
   서로 다른 사용자가 기존 채팅방 하나를 동시에 구매해도 참여 기록과 차감이 각각 저장되고,
   닉네임 순번이 겹치지 않는지 확인한다. 최초 채팅방 생성 경쟁 문제는 이 테스트에 포함하지 않는다.
4. 설정 단위 테스트: `ApplicationContextRunner`로 DB 선택에 따라 컨테이너 빈 하나만 선언되는지 확인한다.
   이 테스트에서는 컨테이너를 자동으로 시작하는 설정을 불러오지 않으므로 Docker가 필요하지 않다.
   지원하지 않는 값이나 빈 문자열을 지정해도 MySQL 빈을 대신 생성하지 않는지 확인한다.
5. 서비스 단위 테스트: 참여자 수를 조회하기 전에 채팅방을 잠그는 순서를 확인한다.

서비스 통합 테스트는 실제 서비스를 호출해 잔액·거래·해금·보상 결과를 확인한다.
두 DB에서 작업별 실제 커밋·롤백과 `READ_COMMITTED` 격리 수준을 유지한다.

## 크레딧 관련 서비스의 트랜잭션

크레딧 변경, 출석·가입·초대 보상, 프로필 수정은 `READ_COMMITTED` 트랜잭션을 사용한다.
사용자 행을 처음 잠글 때 최신 상태를 다시 읽으며, 같은 트랜잭션에서 다시 잠글 때는 진행 중인 변경을 유지한다.
MySQL에서 관련 행이 아직 없을 때 발생할 수 있는 갭 잠금 교착을 피하려면,
이 서비스들을 묶어 호출하는 새 외부 트랜잭션도 같은 격리 수준을 사용해야 한다.
두 사용자의 보상을 함께 처리할 때는 사용자 ID가 작은 순서대로 잠금을 잡는다.

## 채팅방 참여자 집계

PostgreSQL은 집계 쿼리에서 `COUNT(*) ... FOR UPDATE`를 허용하지 않는다.
참여자 수는 Spring Data JPA의 일반 count 조회로 구한다.
참여자 수를 조회하기 전에 채팅방 행의 비관적 쓰기 잠금을 잡는다.
같은 방을 동시에 구매해도 닉네임 순번이 겹치지 않는지 회귀 테스트로 확인한다.

## PR 테스트

`main` 대상 PR에서는 기본 테스트와 MySQL·PostgreSQL 통합 테스트를 각각 실행한다.
DB별 검사는 `testDatabase` 속성에 DB를 지정해 같은 `integrationTest` 작업을 실행한다.
검사에 실패하면 해당 테스트의 보고서를 아티팩트로 남긴다.

## 참고

- [Spring Boot Testcontainers와 Service Connections](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring Boot JDBC·JPA 테스트 설정](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [MySQL 트랜잭션 격리 수준](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html)
- [PostgreSQL SELECT 잠금 제한](https://www.postgresql.org/docs/17/sql-select.html#SQL-FOR-UPDATE-SHARE)
