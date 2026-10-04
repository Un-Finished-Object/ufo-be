-- 정리 결과를 커밋하고, 같은 열 조합의 Unique 인덱스가 없는지 확인한 뒤 실행한다.
ALTER TABLE chat_read_statuses
    ADD CONSTRAINT uk_chat_read_status_user_room UNIQUE (user_id, chat_room_id);
