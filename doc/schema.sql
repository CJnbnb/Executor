-- ============================================================
-- Executor 调度引擎 — 数据库初始化脚本
-- 适用: MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS xxl_job_executor_mq
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE xxl_job_executor_mq;

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
    process         VARCHAR(16)  DEFAULT 'pending'       COMMENT '处理状态: pending=待处理, processing=处理中, exception=异常',
    locked_at       BIGINT       DEFAULT NULL            COMMENT '加锁时间戳(毫秒), 用于超时检测',
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
-- 实时高频任务表
-- 秒级精度，通过时间轮调度
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_scheduled_realtime_task (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '任务唯一ID',
    task_id         VARCHAR(64)  NOT NULL                COMMENT '任务标识',
    task_name       VARCHAR(128) NOT NULL                COMMENT '任务名称',
    biz_name        VARCHAR(64)  NOT NULL                COMMENT '业务线名称',
    biz_group       VARCHAR(64)  NOT NULL                COMMENT '业务分组',
    next_trigger_time BIGINT     DEFAULT NULL            COMMENT '下次触发时间戳（毫秒）',
    last_trigger_time BIGINT     DEFAULT NULL            COMMENT '上次触发时间戳（毫秒）',
    scheduled_conf  VARCHAR(128) DEFAULT NULL            COMMENT 'Cron 表达式',
    scheduled_type  VARCHAR(8)   DEFAULT NULL            COMMENT '调度类型: 1=Cron, 2=一次性',
    process         VARCHAR(16)  DEFAULT 'pending'       COMMENT '处理状态: pending=待处理, processing=处理中',
    enable          CHAR(1)      DEFAULT '1'             COMMENT '启用状态',
    payload         TEXT         NOT NULL                COMMENT '业务负载（JSON）',
    topic           VARCHAR(64)  DEFAULT 'executorPool'  COMMENT '目标 RocketMQ Topic',
    create_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_next_trigger (next_trigger_time),
    INDEX idx_process_enable (process, enable)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时高频任务表';

-- -----------------------------------------------------------
-- 任务事件日志表（状态变更对账）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_event_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     VARCHAR(32)  NOT NULL COMMENT '任务ID',
    task_name   VARCHAR(255) COMMENT '任务名称（冗余）',
    biz_name    VARCHAR(255) COMMENT '业务线名称',
    biz_group   VARCHAR(255) COMMENT '业务分组',
    event_type  VARCHAR(32)  NOT NULL COMMENT '事件类型: SCHEDULED/LOCKED/UNLOCKED/TIMEOUT_RESET',
    from_status VARCHAR(16)  COMMENT '变更前状态',
    to_status   VARCHAR(16)  COMMENT '变更后状态',
    event_msg   VARCHAR(1024) COMMENT '事件备注',
    event_time  DATETIME     NOT NULL COMMENT '事件时间',
    INDEX idx_task_id (task_id),
    INDEX idx_event_time (event_time),
    INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务事件日志-用于对账';

-- -----------------------------------------------------------
-- MQ 发送重试表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS retry_task (
    id                VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '任务ID',
    next_trigger_time BIGINT       COMMENT '下次重试时间戳（毫秒）',
    retry_count       INT          DEFAULT 0 NOT NULL COMMENT '已重试次数',
    args              VARCHAR(4096) COMMENT '任务序列化参数（JSON）',
    create_at         DATETIME     COMMENT '创建时间',
    INDEX idx_next_trigger (next_trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 发送重试补偿表';

-- -----------------------------------------------------------
-- XXL-Job 分布式锁表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS xxl_job_lock (
    lock_name VARCHAR(50) NOT NULL PRIMARY KEY COMMENT '锁名称'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-Job 分布式锁';
