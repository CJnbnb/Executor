CREATE TABLE IF NOT EXISTS user_scheduled_common_task (
    id             VARCHAR(64) PRIMARY KEY,
    task_name      VARCHAR(255),
    biz_name       VARCHAR(255),
    biz_group      VARCHAR(255),
    next_trigger_time BIGINT,
    last_trigger_time BIGINT,
    scheduled_conf VARCHAR(255),
    create_at      VARCHAR(32),
    update_at      VARCHAR(32),
    scheduled_type VARCHAR(4) DEFAULT '1',
    enable         VARCHAR(4) DEFAULT '1',
    process        VARCHAR(16),
    payload        TEXT,
    topic          VARCHAR(255),
    task_id        VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS task_event_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       VARCHAR(64) NOT NULL,
    task_name     VARCHAR(128),
    biz_name      VARCHAR(64),
    biz_group     VARCHAR(64),
    event_type    VARCHAR(32) NOT NULL,
    from_status   VARCHAR(16),
    to_status     VARCHAR(16),
    event_msg     VARCHAR(1024),
    event_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tel_task_id (task_id),
    INDEX idx_tel_event_time (event_time)
);
