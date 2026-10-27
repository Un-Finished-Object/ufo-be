-- 이름과 열 순서가 달라도 같은 열 조합의 UNIQUE 인덱스가 있으면 유지한다.
SET @ufo_read_status_has_unique = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chat_read_statuses'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 2
           AND GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME) = 'chat_room_id,user_id'
           AND SUM(SUB_PART IS NOT NULL) = 0
    ) matching_indexes
);

SET @ufo_read_status_constraint_sql = IF(
    @ufo_read_status_has_unique > 0,
    'SELECT 1',
    'ALTER TABLE chat_read_statuses ADD CONSTRAINT uk_chat_read_status_user_room UNIQUE (user_id, chat_room_id)'
);

PREPARE ufo_read_status_constraint FROM @ufo_read_status_constraint_sql;
EXECUTE ufo_read_status_constraint;
DEALLOCATE PREPARE ufo_read_status_constraint;
