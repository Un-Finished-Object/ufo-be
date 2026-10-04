# 채팅 읽음 데이터 정리

`chat_read_statuses`는 사용자와 채팅방마다 한 행만 저장한다. 읽음 위치는 해당 방에 속한 메시지 ID여야 하며, 이전 메시지의 읽음 요청이 들어와도 줄어들지 않는다.

기존 MySQL 데이터에 중복 행이나 잘못된 읽음 위치가 있으면 배포 전에 아래 절차로 정리한다.

## 적용 전 확인

DB의 데이터와 스키마를 백업하고 복구할 수 있는지 확인한다. 아래 쿼리로 테이블 엔진, `UNIQUE` 인덱스, 읽음 상태를 참조하는 외래 키를 확인한다.

```sql
SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('chat_read_statuses', 'chat_messages');

SHOW INDEX FROM chat_read_statuses;

SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME = 'chat_read_statuses';
```

데이터를 정리할 테이블은 InnoDB여야 한다. 읽음 상태를 참조하는 외래 키가 있으면 참조 데이터를 어떻게 처리할지 먼저 정하고, 이 절차를 그대로 실행하지 않는다. `user_id`와 `chat_room_id` 조합에 `UNIQUE` 인덱스가 이미 있으면 새 제약을 추가하지 않는다. 열 순서나 인덱스 이름은 달라도 된다.

중복 행과 잘못된 읽음 위치는 다음 쿼리로 확인한다. 데이터를 정리한 뒤에는 두 쿼리 모두 결과가 없어야 한다.

```sql
SELECT user_id, chat_room_id, COUNT(*) AS row_count
FROM chat_read_statuses
GROUP BY user_id, chat_room_id
HAVING COUNT(*) > 1;

SELECT s.chat_read_status_id, s.user_id, s.chat_room_id, s.last_read_message_id
FROM chat_read_statuses s
LEFT JOIN chat_messages m
  ON m.chat_message_id = s.last_read_message_id
  AND m.chat_room_id = s.chat_room_id
WHERE s.last_read_message_id IS NOT NULL AND m.chat_message_id IS NULL;
```

## 데이터 정리

애플리케이션과 배치 등에서 두 테이블의 데이터를 추가·수정·삭제하는 작업을 모두 중단한다. 진행 중인 트랜잭션이 끝난 뒤 데이터를 정리한다. `UNIQUE` 제약을 적용하고 수정한 코드를 배포할 때까지 쓰기 작업을 재개하지 않는다.

중복 행은 `chat_read_status_id`가 가장 작은 행 하나만 남긴다. 해당 방에 존재하는 메시지 ID 중 가장 큰 값을 저장하고, 그 ID를 기록한 행에서 가장 늦은 `read_at`을 가져온다. 유효한 읽음 위치가 하나도 없으면 메시지 ID와 읽음 시각을 모두 `NULL`로 바꾼다. 읽음 위치를 임의로 정하지 않기 때문에 일부 메시지가 다시 안 읽은 것으로 표시될 수 있다. 삭제 처리된 메시지도 DB에 남아 있으면 유효한 메시지로 취급한다.

저장소 루트에서 MySQL 클라이언트를 실행한 뒤, 연결을 끊지 않고 아래 명령을 순서대로 실행한다. `SOURCE`는 MySQL 클라이언트 명령이다. DB 관리 도구를 사용한다면 SQL 파일의 내용을 붙여 넣고 연결을 유지한 채 실행한다.

```sql
START TRANSACTION;
SOURCE scripts/database/chat-read-status-repair.sql;

SELECT COUNT(*) AS expected_rows FROM ufo_read_status_repair;
SELECT COUNT(*) AS actual_rows FROM chat_read_statuses;

SELECT r.*
FROM ufo_read_status_repair r
JOIN chat_read_statuses s ON s.chat_read_status_id = r.keeper_id
WHERE NOT (s.last_read_message_id <=> r.last_read_message_id)
   OR NOT (s.read_at <=> r.read_at);
```

두 행 수가 같고 마지막 쿼리에 결과가 없는지 확인한다. 앞에서 사용한 중복 행·잘못된 읽음 위치 확인 쿼리도 다시 실행한다. 오류가 났거나 결과가 예상과 다르면 `ROLLBACK`으로 원래 데이터를 되돌린다. 문제가 없을 때만 `COMMIT`한다. SQL 파일은 자동으로 커밋하지 않는다.

커밋 또는 롤백 후에는 연결을 끊기 전에 아래 명령으로 임시 테이블을 삭제한다. 연결을 유지한 채 정리 SQL을 다시 실행하려면 이전에 생성된 임시 테이블부터 삭제해야 한다.

```sql
DROP TEMPORARY TABLE IF EXISTS ufo_read_status_repair;
DROP TEMPORARY TABLE IF EXISTS ufo_read_status_groups;
```

## UNIQUE 제약 적용

정리한 데이터를 커밋한 뒤, 두 열의 조합에 `UNIQUE` 인덱스가 없으면 아래 파일을 실행한다.

```sql
SOURCE scripts/database/chat-read-status-constraint.sql;
SHOW INDEX FROM chat_read_statuses;
```

MySQL은 `ALTER TABLE`을 실행하면 자동으로 커밋하므로 `ROLLBACK`으로 되돌릴 수 없다. 데이터 정리 결과를 확인하고 커밋한 뒤에 제약을 추가한다. 자세한 동작은 [MySQL 문서](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)를 참고한다.

제약 추가에 실패하면 쓰기 작업을 중단한 상태에서 원인을 확인한다. 이미 커밋한 데이터는 롤백할 수 없다. 수정한 코드를 배포한 뒤, 외부 요청을 다시 받기 전에 중복 행이나 잘못된 읽음 위치가 없는지 확인한다. 읽음 처리와 안 읽은 메시지 조회도 정상 동작하는지 확인한다.
