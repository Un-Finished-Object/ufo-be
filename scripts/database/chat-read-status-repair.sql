-- 관련 쓰기 작업을 모두 중단한 뒤 트랜잭션 안에서 실행한다. 결과를 확인한 뒤 커밋한다.
-- 스크립트를 통해 생성된 임시 테이블은 DB 연결을 종료하면 자동으로 삭제된다.
-- 연결을 끊지 않고 이 스크립트를 다시 실행하려면 임시 테이블을 먼저 삭제한다.
CREATE TEMPORARY TABLE ufo_read_status_groups AS
SELECT s.user_id,
       s.chat_room_id,
       MIN(s.chat_read_status_id) AS keeper_id,
       MAX(m.chat_message_id) AS last_read_message_id
FROM chat_read_statuses s
LEFT JOIN chat_messages m
    ON m.chat_message_id = s.last_read_message_id
    AND m.chat_room_id = s.chat_room_id
GROUP BY s.user_id, s.chat_room_id;

CREATE TEMPORARY TABLE ufo_read_status_repair AS
SELECT g.user_id,
       g.chat_room_id,
       g.keeper_id,
       g.last_read_message_id,
       s.read_at
FROM ufo_read_status_groups g
LEFT JOIN (
    SELECT user_id,
           chat_room_id,
           last_read_message_id,
           read_at,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, chat_room_id, last_read_message_id
               ORDER BY CASE WHEN read_at IS NULL THEN 1 ELSE 0 END,
                        read_at DESC, chat_read_status_id DESC
           ) AS read_rank
    FROM chat_read_statuses
) s
    ON s.user_id = g.user_id AND s.chat_room_id = g.chat_room_id
    AND s.last_read_message_id = g.last_read_message_id
    AND s.read_rank = 1;

UPDATE chat_read_statuses
SET last_read_message_id = (
    SELECT r.last_read_message_id
    FROM ufo_read_status_repair r
    WHERE r.keeper_id = chat_read_statuses.chat_read_status_id
);

UPDATE chat_read_statuses
SET read_at = (
    SELECT r.read_at
    FROM ufo_read_status_repair r
    WHERE r.keeper_id = chat_read_statuses.chat_read_status_id
);

DELETE FROM chat_read_statuses
WHERE chat_read_status_id NOT IN (SELECT keeper_id FROM ufo_read_status_repair);
