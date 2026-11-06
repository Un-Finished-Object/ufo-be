CREATE TABLE chat_event_outbox (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    room_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_json TEXT NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    PRIMARY KEY (event_id),
    INDEX idx_chat_outbox_room_pending (room_id, published_at, event_id),
    INDEX idx_chat_outbox_due (published_at, next_attempt_at, event_id)
);
