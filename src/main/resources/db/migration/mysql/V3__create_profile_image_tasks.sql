CREATE TABLE profile_image_tasks (
    task_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    user_id BIGINT NOT NULL,
    task_type ENUM('LINK', 'DELETE') NOT NULL,
    image_key VARCHAR(255) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    INDEX idx_profile_image_task_due (next_attempt_at, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
