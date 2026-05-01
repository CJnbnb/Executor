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
