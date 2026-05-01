-- ============================================================
-- Executor 调度引擎 — 数据库初始化脚本
-- 适用: MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS xxl_job_executor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE xxl_job_executor;

-- -----------------------------------------------------------
-- 通用定时任务表
-- 所有通过 SDK 注册的定时任务统一存储在此表中
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_scheduled_common_task (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '任务唯一ID（时间戳+随机数）',
    task_id         VARCHAR(64)  DEFAULT NULL            COMMENT '任务标识（与 id 相同）',
    task_name       VARCHAR(128) NOT NULL                COMMENT '任务名称',
    biz_name        VARCHAR(64)  NOT NULL                COMMENT '业务线名称（如 order、user）',
    biz_group       VARCHAR(64)  NOT NULL                COMMENT '业务分组（如 daily_report、cleanup）',
    next_trigger_time BIGINT     DEFAULT NULL            COMMENT '下次触发时间戳（毫秒）',
    last_trigger_time BIGINT     DEFAULT NULL            COMMENT '上次触发时间戳（毫秒）',
    scheduled_conf  VARCHAR(128) DEFAULT NULL            COMMENT 'Cron 表达式，为空则表示一次性任务',
    scheduled_type  VARCHAR(8)   DEFAULT NULL            COMMENT '调度类型: 1=Cron, 2=一次性',
    process         VARCHAR(16)  DEFAULT NULL            COMMENT '处理状态: NULL=待处理, processing=处理中, done=已完成',
    enable          CHAR(1)      DEFAULT '1'             COMMENT '启用状态: 1=启用, 0=禁用',
    payload         TEXT         DEFAULT NULL            COMMENT '业务负载（JSON 字符串）',
    topic           VARCHAR(64)  DEFAULT 'executorPool'  COMMENT '目标 RocketMQ Topic',
    create_at       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_biz_name_group (biz_name, biz_group),
    INDEX idx_next_trigger (next_trigger_time),
    INDEX idx_process (process),
    INDEX idx_enable (enable)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用定时任务表';

-- -----------------------------------------------------------
-- 任务执行日志表（预留，后续版本实现）
-- -----------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS task_execution_log (
--     id          BIGINT AUTO_INCREMENT PRIMARY KEY,
--     task_id     VARCHAR(64)  NOT NULL COMMENT '关联任务ID',
--     task_name   VARCHAR(128) COMMENT '任务名称（冗余）',
--     biz_name    VARCHAR(64)  COMMENT '业务线名称',
--     biz_group   VARCHAR(64)  COMMENT '业务分组',
--     status      VARCHAR(16)  NOT NULL COMMENT '执行状态: success/fail/timeout',
--     cost_ms     INT          COMMENT '执行耗时（毫秒）',
--     error_msg   TEXT         COMMENT '失败原因',
--     executed_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
--     INDEX idx_task_id (task_id),
--     INDEX idx_executed_at (executed_at)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';
